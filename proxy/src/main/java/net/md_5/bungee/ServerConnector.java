package net.md_5.bungee;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.event.BrandSendEvent;
import net.md_5.bungee.api.event.ChannelWrapperEvent;
import net.md_5.bungee.api.event.EncryptionRequestEvent;
import net.md_5.bungee.api.event.ServerConnectEvent;
import net.md_5.bungee.api.event.ServerConnectedEvent;
import net.md_5.bungee.api.event.ServerKickEvent;
import net.md_5.bungee.api.event.ServerSwitchEvent;
import net.md_5.bungee.api.score.Objective;
import net.md_5.bungee.api.score.Score;
import net.md_5.bungee.api.score.Scoreboard;
import net.md_5.bungee.api.score.Team;
import net.md_5.bungee.connection.CancelSendSignal;
import net.md_5.bungee.connection.DownstreamBridge;
import net.md_5.bungee.connection.LoginResult;
import net.md_5.bungee.forge.ForgeConstants;
import net.md_5.bungee.forge.ForgeServerHandler;
import net.md_5.bungee.forge.ForgeUtils;
import net.md_5.bungee.netty.ChannelWrapper;
import net.md_5.bungee.netty.HandlerBoss;
import net.md_5.bungee.netty.PacketHandler;
import net.md_5.bungee.protocol.DefinedPacket;
import net.md_5.bungee.protocol.PacketWrapper;
import net.md_5.bungee.protocol.Protocol;
import net.md_5.bungee.protocol.ProtocolConstants;
import net.md_5.bungee.protocol.packet.BundleDelimiter;
import net.md_5.bungee.protocol.packet.CookieRequest;
import net.md_5.bungee.protocol.packet.CookieResponse;
import net.md_5.bungee.protocol.packet.EncryptionRequest;
import net.md_5.bungee.protocol.packet.EntityStatus;
import net.md_5.bungee.protocol.packet.GameState;
import net.md_5.bungee.protocol.packet.Handshake;
import net.md_5.bungee.protocol.packet.Kick;
import net.md_5.bungee.protocol.packet.Login;
import net.md_5.bungee.protocol.packet.LoginPayloadRequest;
import net.md_5.bungee.protocol.packet.LoginPayloadResponse;
import net.md_5.bungee.protocol.packet.LoginRequest;
import net.md_5.bungee.protocol.packet.LoginSuccess;
import net.md_5.bungee.protocol.packet.PluginMessage;
import net.md_5.bungee.protocol.packet.Respawn;
import net.md_5.bungee.protocol.packet.ScoreboardObjective;
import net.md_5.bungee.protocol.packet.ScoreboardScore;
import net.md_5.bungee.protocol.packet.ScoreboardScoreReset;
import net.md_5.bungee.protocol.packet.SetCompression;
import net.md_5.bungee.protocol.packet.StartConfiguration;
import net.md_5.bungee.protocol.packet.ViewDistance;
import net.md_5.bungee.protocol.util.Either;
import net.md_5.bungee.util.AddressUtil;
import net.md_5.bungee.util.BufUtil;
import net.md_5.bungee.util.QuietException;

@RequiredArgsConstructor
public class ServerConnector extends PacketHandler
{

    private final ProxyServer bungee;
    private ChannelWrapper ch;
    private final UserConnection user;
    private final BungeeServerInfo target;
    private State thisState = State.LOGIN_SUCCESS;
    @Getter
    private ForgeServerHandler handshakeHandler;
    private boolean obsolete;

    private enum State
    {

        LOGIN_SUCCESS, LOGIN, FINISHED;
    }

    @Override
    public void exception(Throwable t) throws Exception
    {
        if ( obsolete )
        {
            return;
        }

        String message = ChatColor.RED + "Exception Connecting: " + Util.exception( t );
        if ( user.getServer() == null )
        {
            user.disconnect( message );
        } else
        {
            user.sendMessage( message );
        }
    }

    @Override
    public void connected(ChannelWrapper channel) throws Exception
    {
        channel.setVersion( user.getPendingConnection().getVersion() );
        this.ch = channel;

        ChannelWrapperEvent event = new ChannelWrapperEvent( user, ch, target, handshakeHandler );
        bungee.getPluginManager().callEvent( event );

        if ( event.isCancelled() )
        {
            this.ch = (ChannelWrapper) event.getChannelWrapper();
            this.handshakeHandler = (ForgeServerHandler) event.getHandshakeHandler();
            return;
        }

        this.handshakeHandler = new ForgeServerHandler( user, ch, target );
        Handshake originalHandshake = user.getPendingConnection().getHandshake();
        Handshake copiedHandshake = new Handshake( originalHandshake.getProtocolVersion(), originalHandshake.getHost(), originalHandshake.getPort(), 2 );

        if ( BungeeCord.getInstance().config.isIpForward() && user.getSocketAddress() instanceof InetSocketAddress )
        {
            String newHost = copiedHandshake.getHost() + "\00" + AddressUtil.sanitizeAddress( user.getAddress() ) + "\00" + user.getUUID();

            LoginResult profile = user.getPendingConnection().getLoginProfile();
            if ( profile != null && profile.getProperties() != null && profile.getProperties().length > 0 )
            {
                newHost += "\00" + LoginResult.GSON.toJson( profile.getProperties() );
            }
            copiedHandshake.setHost( newHost );
        } else if ( !user.getExtraDataInHandshake().isEmpty() )
        {
            // Only restore the extra data if IP forwarding is off.
            // TODO: Add support for this data with IP forwarding.
            copiedHandshake.setHost( copiedHandshake.getHost() + user.getExtraDataInHandshake() );
        }

        channel.write( copiedHandshake );

        channel.setProtocol( Protocol.LOGIN );
        channel.write( new LoginRequest( user.getName(), null, user.getRewriteId() ) );
    }

    @Override
    public void disconnected(ChannelWrapper channel) throws Exception
    {
        user.getPendingConnects().remove( target );

        if ( user.getServer() == null && !obsolete && user.getPendingConnects().isEmpty() && thisState == State.LOGIN_SUCCESS )
        {
            // this is called if we get disconnected but not have received any response after we send the handshake
            // in this case probably an exception was thrown because the handshake could not be read correctly
            // because of the extra ip forward data, also we skip the disconnect if another server is also in the
            // pendingConnects queue because we don't want to lose the player
            user.disconnect( "Unexpected disconnect during server login, did you forget to enable BungeeCord / IP forwarding on your server?" );
        }
    }

    @Override
    public void handle(PacketWrapper packet) throws Exception
    {
        if ( packet.packet == null )
        {
            throw new QuietException( "Unexpected packet received during server login process!\n" + BufUtil.dump( packet.buf, 16 ) );
        }
    }

    @Override
    public void handle(LoginSuccess loginSuccess) throws Exception
    {
        Preconditions.checkState( thisState == State.LOGIN_SUCCESS, "Not expecting LOGIN_SUCCESS" );
        if ( user.getPendingConnection().getVersion() >= ProtocolConstants.MINECRAFT_1_20_2 )
        {
            ServerConnection server = new ServerConnection( ch, target );
            cutThrough( server );
        } else
        {
            ch.setProtocol( Protocol.GAME );
            thisState = State.LOGIN;
        }

        // Only reset the Forge client when:
        // 1) The user is switching servers (so has a current server)
        // 2) The handshake is complete
        // 3) The user is currently on a modded server (if we are on a vanilla server,
        //    we may be heading for another vanilla server, so we don't need to reset.)
        //
        // user.getServer() gets the user's CURRENT server, not the one we are trying
        // to connect to.
        //
        // We will reset the connection later if the current server is vanilla, and
        // we need to switch to a modded connection. However, we always need to reset the
        // connection when we have a modded server regardless of where we go - doing it
        // here makes sense.
        if ( user.getServer() != null && user.getForgeClientHandler().isHandshakeComplete()
                && user.getServer().isForgeServer() )
        {
            user.getForgeClientHandler().resetHandshake();
        }

        throw CancelSendSignal.INSTANCE;
    }

    @Override
    public void handle(SetCompression setCompression) throws Exception
    {
        ch.setCompressionThreshold( setCompression.getThreshold() );
    }

    @Override
    public void handle(CookieRequest cookieRequest) throws Exception
    {
        user.retrieveCookie( cookieRequest.getCookie() ).thenAccept( (cookie) -> ch.write( new CookieResponse( cookieRequest.getCookie(), cookie ) ) );
    }

    @Override
    public void handle(Login login) throws Exception
    {
        Preconditions.checkState( thisState == State.LOGIN, "Not expecting LOGIN" );

        ServerConnection server = new ServerConnection( ch, target );
        handleLogin( bungee, ch, user, target, handshakeHandler, server, login );
        cutThrough( server );
    }

    public static void handleLogin(ProxyServer bungee, ChannelWrapper ch, UserConnection user, BungeeServerInfo target, ForgeServerHandler handshakeHandler, ServerConnection server, Login login) throws Exception
    {
        ServerConnectedEvent event = new ServerConnectedEvent( user, server );
        bungee.getPluginManager().callEvent( event );

        ch.write( BungeeCord.getInstance().registerChannels( user.getPendingConnection().getVersion() ) );
        Queue<DefinedPacket> packetQueue = target.getPacketQueue();
        synchronized ( packetQueue )
        {
            while ( !packetQueue.isEmpty() )
            {
                ch.write( packetQueue.poll() );
            }
        }

        PluginMessage brandMessage = user.getPendingConnection().getBrandMessage();
        if ( brandMessage != null )
        {
            ch.write( brandMessage );
        }

        Set<String> registeredChannels = user.getPendingConnection().getRegisteredChannels();
        if ( !registeredChannels.isEmpty() )
        {
            ch.write( new PluginMessage( user.getPendingConnection().getVersion() >= ProtocolConstants.MINECRAFT_1_13 ? "minecraft:register" : "REGISTER", Joiner.on( "\0" ).join( registeredChannels ).getBytes( StandardCharsets.UTF_8 ), false ) );
        }

        if ( user.getSettings() != null )
        {
            ch.write( user.getSettings() );
        }

        if ( user.getForgeClientHandler().getClientModList() == null && !user.getForgeClientHandler().isHandshakeComplete() ) // Vanilla
        {
            user.getForgeClientHandler().setHandshakeComplete();
        }

        if ( user.getServer() == null || user.getPendingConnection().getVersion() >= ProtocolConstants.MINECRAFT_1_16 )
        {
            // Once again, first connection
            user.setClientEntityId( login.getEntityId() );
            user.setServerEntityId( login.getEntityId() );

            // Set tab list size, TODO: what shall we do about packet mutability
            Login modLogin = new Login( login.getEntityId(), login.isHardcore(), login.getGameMode(), login.getPreviousGameMode(), login.getWorldNames(), login.getDimensions(), login.getDimension(), login.getWorldName(), login.getSeed(), login.getDifficulty(),
                    (byte) user.getPendingConnection().getListener().getTabListSize(), login.getLevelType(), login.getViewDistance(), login.getSimulationDistance(), login.isReducedDebugInfo(), login.isNormalRespawn(), login.isLimitedCrafting(), login.isDebug(), login.isFlat(), login.getDeathLocation(),
                    login.getPortalCooldown(), login.getSeaLevel(), login.isSecureProfile() );

            user.unsafe().sendPacket( modLogin );

            if ( user.getDimension() != null )
            {
                user.getTabListHandler().onServerChange();

                user.getServerSentScoreboard().clear();

                for ( UUID bossbar : user.getSentBossBars() )
                {
                    // Send remove bossbar packet
                    user.unsafe().sendPacket( new net.md_5.bungee.protocol.packet.BossBar( bossbar, 1 ) );
                }
                user.getSentBossBars().clear();

                user.unsafe().sendPacket( new Respawn( login.getDimension(), login.getWorldName(), login.getSeed(), login.getDifficulty(), login.getGameMode(), login.getPreviousGameMode(), login.getLevelType(), login.isDebug(), login.isFlat(), (byte) 0, login.getDeathLocation(),
                        login.getPortalCooldown(), login.getSeaLevel() ) );
            } else
            {
                user.unsafe().sendPacket( BungeeCord.getInstance().registerChannels( user.getPendingConnection().getVersion() ) );

                ByteBuf brand = ByteBufAllocator.DEFAULT.heapBuffer();

                String bugnee = bungee.getName() + " (" + bungee.getVersion() + ")";

                BrandSendEvent brandSendEvent = new BrandSendEvent( user, false, bugnee, bugnee, null );
                BrandSendEvent ret = bungee.getPluginManager().callEvent( brandSendEvent );

                if ( ret.isOverwrite() )
                {
                    DefinedPacket.writeString( ret.getBrand(), brand );
                } else
                {
                    DefinedPacket.writeString( bugnee, brand );
                }

                user.unsafe().sendPacket( new PluginMessage( user.getPendingConnection().getVersion() >= ProtocolConstants.MINECRAFT_1_13 ? "minecraft:brand" : "MC|Brand", DefinedPacket.toArray( brand ), handshakeHandler != null && handshakeHandler.isServerForge() ) );

                brand.release();
            }

            user.setDimension( login.getDimension() );
        } else
        {
            user.getServer().setObsolete( true );
            user.getTabListHandler().onServerChange();

            Scoreboard serverScoreboard = user.getServerSentScoreboard();
            for ( Objective objective : serverScoreboard.getObjectives() )
            {
                user.unsafe().sendPacket( new ScoreboardObjective(
                        objective.getName(),
                        ( user.getPendingConnection().getVersion() >= ProtocolConstants.MINECRAFT_1_13 ) ? Either.right( user.getChatSerializer().deserialize( objective.getValue() ) ) : Either.left( objective.getValue() ),
                        ScoreboardObjective.HealthDisplay.fromString( objective.getType() ),
                        (byte) 1, null )
                );
            }
            for ( Score score : serverScoreboard.getScores() )
            {
                if ( user.getPendingConnection().getVersion() >= ProtocolConstants.MINECRAFT_1_20_3 )
                {
                    user.unsafe().sendPacket( new ScoreboardScoreReset( score.getItemName(), null ) );
                } else
                {
                    user.unsafe().sendPacket( new ScoreboardScore( score.getItemName(), (byte) 1, score.getScoreName(), score.getValue(), null, null ) );
                }
            }
            for ( Team team : serverScoreboard.getTeams() )
            {
                user.unsafe().sendPacket( new net.md_5.bungee.protocol.packet.Team( team.getName() ) );
            }
            serverScoreboard.clear();

            for ( UUID bossbar : user.getSentBossBars() )
            {
                // Send remove bossbar packet
                user.unsafe().sendPacket( new net.md_5.bungee.protocol.packet.BossBar( bossbar, 1 ) );
            }
            user.getSentBossBars().clear();

            // Update debug info from login packet
            user.unsafe().sendPacket( new EntityStatus( user.getClientEntityId(), login.isReducedDebugInfo() ? EntityStatus.DEBUG_INFO_REDUCED : EntityStatus.DEBUG_INFO_NORMAL ) );
            // And immediate respawn
            if ( user.getPendingConnection().getVersion() >= ProtocolConstants.MINECRAFT_1_15 )
            {
                user.unsafe().sendPacket( new GameState( GameState.IMMEDIATE_RESPAWN, login.isNormalRespawn() ? 0 : 1 ) );
            }

            user.setDimensionChange( true );
            if ( login.getDimension() == user.getDimension() )
            {
                user.unsafe().sendPacket( new Respawn( (Integer) login.getDimension() >= 0 ? -1 : 0, login.getWorldName(), login.getSeed(), login.getDifficulty(), login.getGameMode(), login.getPreviousGameMode(), login.getLevelType(), login.isDebug(), login.isFlat(),
                        (byte) 0, login.getDeathLocation(), login.getPortalCooldown(), login.getSeaLevel() ) );
            }

            user.setServerEntityId( login.getEntityId() );
            user.unsafe().sendPacket( new Respawn( login.getDimension(), login.getWorldName(), login.getSeed(), login.getDifficulty(), login.getGameMode(), login.getPreviousGameMode(), login.getLevelType(), login.isDebug(), login.isFlat(),
                    (byte) 0, login.getDeathLocation(), login.getPortalCooldown(), login.getSeaLevel() ) );
            if ( user.getPendingConnection().getVersion() >= ProtocolConstants.MINECRAFT_1_14 )
            {
                user.unsafe().sendPacket( new ViewDistance( login.getViewDistance() ) );
            }
            user.setDimension( login.getDimension() );
        }
    }

    private void cutThrough(ServerConnection server)
    {
        // TODO: Fix this?
        if ( !user.isActive() )
        {
            server.disconnect( "Quitting" );
            bungee.getLogger().log( Level.WARNING, "[{0}] No client connected for pending server!", user );
            return;
        }

        if ( user.getPendingConnection().getVersion() >= ProtocolConstants.MINECRAFT_1_20_2 )
        {
            if ( user.getServer() != null )
            {
                if ( user.getCh().getEncodeProtocol() != Protocol.CONFIGURATION )
                {
                    if ( user.isBundling() )
                    {
                        user.toggleBundling();
                        user.unsafe().sendPacket( new BundleDelimiter() );
                    }
                    user.unsafe().sendPacket( new StartConfiguration() );
                }
            } else
            {
                LoginResult loginProfile = user.getPendingConnection().getLoginProfile();
                user.unsafe().sendPacket( new LoginSuccess( user.getRewriteId(), user.getName(), ( loginProfile == null ) ? null : loginProfile.getProperties() ) );
                user.getCh().setEncodeProtocol( Protocol.CONFIGURATION );
            }
        }

        // Remove from old servers
        if ( user.getServer() != null )
        {
            user.getServer().disconnect( "Quitting" );
        }

        // Add to new server
        // TODO: Move this to the connected() method of DownstreamBridge
        target.addPlayer( user );
        user.getPendingConnects().remove( target );
        user.setServerJoinQueue( null );
        user.setDimensionChange( false );

        ServerInfo from = ( user.getServer() == null ) ? null : user.getServer().getInfo();
        user.setServer( server );
        ch.getHandle().pipeline().get( HandlerBoss.class ).setHandler( new DownstreamBridge( bungee, user, server ) );

        bungee.getPluginManager().callEvent( new ServerSwitchEvent( user, from ) );

        thisState = State.FINISHED;

        throw CancelSendSignal.INSTANCE;
    }

    @Override
    public void handle(EncryptionRequest encryptionRequest) throws Exception
    {
        EncryptionRequestEvent event = new EncryptionRequestEvent( user, ch, encryptionRequest );
        bungee.getPluginManager().callEvent( event );

        if ( event.isCancelled() )
        {
            this.ch = (ChannelWrapper) event.getChannelWrapper();
            throw CancelSendSignal.INSTANCE;
        }

        throw new QuietException( "Server is online mode!" );
    }

    @Override
    public void handle(Kick kick) throws Exception
    {
        ServerInfo def = user.updateAndGetNextServer( target );
        ServerKickEvent event = new ServerKickEvent( user, target, new BaseComponent[]
        {
            kick.getMessage()
        }, def, ServerKickEvent.State.CONNECTING );
        if ( event.getKickReason().toLowerCase( Locale.ROOT ).contains( "outdated" ) && def != null )
        {
            // Pre cancel the event if we are going to try another server
            event.setCancelled( true );
        }
        bungee.getPluginManager().callEvent( event );
        if ( event.isCancelled() && event.getCancelServer() != null )
        {
            obsolete = true;
            user.connect( event.getCancelServer(), ServerConnectEvent.Reason.KICK_REDIRECT );
            throw CancelSendSignal.INSTANCE;
        }

        String message = bungee.getTranslation( "connect_kick", target.getName(), event.getKickReason() );
        if ( user.isDimensionChange() )
        {
            user.disconnect( message );
        } else
        {
            user.sendMessage( message );
        }

        throw CancelSendSignal.INSTANCE;
    }

    @Override
    public void handle(PluginMessage pluginMessage) throws Exception
    {
        if ( BungeeCord.getInstance().config.isForgeSupport() )
        {
            if ( pluginMessage.getTag().equals( ForgeConstants.FML_REGISTER ) )
            {
                Set<String> channels = ForgeUtils.readRegisteredChannels( pluginMessage );
                boolean isForgeServer = false;
                for ( String channel : channels )
                {
                    if ( channel.equals( ForgeConstants.FML_HANDSHAKE_TAG ) )
                    {
                        // If we have a completed handshake and we have been asked to register a FML|HS
                        // packet, let's send the reset packet now. Then, we can continue the message sending.
                        // The handshake will not be complete if we reset this earlier.
                        if ( user.getServer() != null && user.getForgeClientHandler().isHandshakeComplete() )
                        {
                            user.getForgeClientHandler().resetHandshake();
                        }

                        isForgeServer = true;
                        break;
                    }
                }

                if ( isForgeServer && !this.handshakeHandler.isServerForge() )
                {
                    // We now set the server-side handshake handler for the client to this.
                    handshakeHandler.setServerAsForgeServer();
                    user.setForgeServerHandler( handshakeHandler );
                }
            }

            if ( pluginMessage.getTag().equals( ForgeConstants.FML_HANDSHAKE_TAG ) || pluginMessage.getTag().equals( ForgeConstants.FORGE_REGISTER ) )
            {
                this.handshakeHandler.handle( pluginMessage );

                // We send the message as part of the handler, so don't send it here.
                throw CancelSendSignal.INSTANCE;
            }
        }

        // We have to forward these to the user, especially with Forge as stuff might break
        // This includes any REGISTER messages we intercepted earlier.
        user.unsafe().sendPacket( pluginMessage );
    }

    @Override
    public void handle(LoginPayloadRequest loginPayloadRequest)
    {
        ch.write( new LoginPayloadResponse( loginPayloadRequest.getId(), null ) );
    }

    @Override
    public String toString()
    {
        return "[" + user.getName() + "] <-> ServerConnector [" + target.getName() + "]";
    }
}
