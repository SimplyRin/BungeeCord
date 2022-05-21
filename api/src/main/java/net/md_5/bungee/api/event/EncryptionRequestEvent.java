package net.md_5.bungee.api.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Cancellable;
import net.md_5.bungee.api.plugin.Event;
import net.md_5.bungee.protocol.packet.EncryptionRequest;

@Data
@RequiredArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class EncryptionRequestEvent extends Event implements Cancellable
{
    /**
     * Whether the event is cancelled.
     */
    private boolean cancelled;

    private final ProxiedPlayer player;

    // ChannelWrapper
    private final Object channelWrapper;

    // EncryptionRequest
    private final EncryptionRequest encryptionRequest;
}
