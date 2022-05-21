package net.md_5.bungee.api.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Cancellable;
import net.md_5.bungee.api.plugin.Event;

@Data
@RequiredArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class ChannelWrapperEvent extends Event implements Cancellable
{
    /**
     * Whether the event is cancelled.
     */
    private boolean cancelled;

    private final ProxiedPlayer player;

    // ChannelWrapper
    private final Object channelWrapper;

    // BungeeServerInfo
    private final Object target;

    // ForgeServerHandler
    private final Object handshakeHandler;
}
