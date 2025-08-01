package net.md_5.bungee.protocol.packet;

import io.netty.buffer.ByteBuf;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.chat.ComponentSerializer;
import net.md_5.bungee.protocol.AbstractPacketHandler;
import net.md_5.bungee.protocol.DefinedPacket;
import net.md_5.bungee.protocol.ProtocolConstants;
import net.md_5.bungee.protocol.data.PlayerPublicKey;
import net.md_5.bungee.protocol.data.Property;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class PlayerListItem extends DefinedPacket
{

    private Action action;
    private Item[] items;

    @Override
    public void read(ByteBuf buf, ProtocolConstants.Direction direction, int protocolVersion)
    {
        action = Action.values()[DefinedPacket.readVarInt( buf )];
        items = new Item[ DefinedPacket.readVarInt( buf ) ];
        for ( int i = 0; i < items.length; i++ )
        {
            Item item = items[i] = new Item();
            item.setUuid( DefinedPacket.readUUID( buf ) );
            switch ( action )
            {
                case ADD_PLAYER:
                    item.username = DefinedPacket.readString( buf );
                    item.properties = DefinedPacket.readProperties( buf );
                    item.gamemode = DefinedPacket.readVarInt( buf );
                    item.ping = DefinedPacket.readVarInt( buf );
                    if ( buf.readBoolean() )
                    {
                        item.displayName = DefinedPacket.readBaseComponent( buf, protocolVersion );
                    }
                    if ( protocolVersion >= ProtocolConstants.MINECRAFT_1_19 )
                    {
                        item.publicKey = readPublicKey( buf );
                    }
                    break;
                case UPDATE_GAMEMODE:
                    item.gamemode = DefinedPacket.readVarInt( buf );
                    break;
                case UPDATE_LATENCY:
                    item.ping = DefinedPacket.readVarInt( buf );
                    break;
                case UPDATE_DISPLAY_NAME:
                    if ( buf.readBoolean() )
                    {
                        item.displayName = DefinedPacket.readBaseComponent( buf, protocolVersion );
                    }
            }
        }
    }

    @Override
    public void write(ByteBuf buf, ProtocolConstants.Direction direction, int protocolVersion)
    {
        DefinedPacket.writeVarInt( action.ordinal(), buf );
        DefinedPacket.writeVarInt( items.length, buf );
        for ( Item item : items )
        {
            DefinedPacket.writeUUID( item.uuid, buf );
            switch ( action )
            {
                case ADD_PLAYER:
                    DefinedPacket.writeString( item.username, buf );
                    DefinedPacket.writeProperties( item.properties, buf );
                    DefinedPacket.writeVarInt( item.gamemode, buf );
                    DefinedPacket.writeVarInt( item.ping, buf );
                    buf.writeBoolean( item.displayName != null );
                    if ( item.displayName != null )
                    {
                        DefinedPacket.writeBaseComponent( item.displayName, buf, protocolVersion );
                    }
                    if ( protocolVersion >= ProtocolConstants.MINECRAFT_1_19 )
                    {
                        writePublicKey( item.publicKey, buf );
                    }
                    break;
                case UPDATE_GAMEMODE:
                    DefinedPacket.writeVarInt( item.gamemode, buf );
                    break;
                case UPDATE_LATENCY:
                    DefinedPacket.writeVarInt( item.ping, buf );
                    break;
                case UPDATE_DISPLAY_NAME:
                    buf.writeBoolean( item.displayName != null );
                    if ( item.displayName != null )
                    {
                        DefinedPacket.writeBaseComponent( item.displayName, buf, protocolVersion );
                    }
                    break;
            }
        }
    }

    @Override
    public void handle(AbstractPacketHandler handler) throws Exception
    {
        handler.handle( this );
    }

    public static enum Action
    {

        ADD_PLAYER,
        UPDATE_GAMEMODE,
        UPDATE_LATENCY,
        UPDATE_DISPLAY_NAME,
        REMOVE_PLAYER;
    }

    @Data
    public static class Item
    {

        // ALL
        UUID uuid;

        // ADD_PLAYER
        String username;
        Property[] properties;

        UUID chatSessionId;
        PlayerPublicKey publicKey;

        // UPDATE_LISTED
        Boolean listed;

        // ADD_PLAYER & UPDATE_GAMEMODE
        Integer gamemode;

        // ADD_PLAYER & UPDATE_LATENCY
        Integer ping;

        // ADD_PLAYER & UPDATE_DISPLAY_NAME
        BaseComponent displayName;

        public void setDisplayName(BaseComponent displayName)
        {
            this.displayName = displayName;
        }

        public void setDisplayName(String displayName)
        {
            try
            {
                this.displayName = ComponentSerializer.deserialize( displayName );
            } catch ( Exception e )
            {
                this.displayName = new TextComponent( displayName );
            }
        }

        // UPDATE_LIST_ORDER 1.21.2
        Integer listOrder;

        // UPDATE_HAT 1.21.4
        Boolean showHat;
    }
}
