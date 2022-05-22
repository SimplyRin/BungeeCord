package net.md_5.bungee.api.event;

import com.mojang.brigadier.tree.RootCommandNode;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Event;
import net.md_5.bungee.protocol.packet.Commands;

@Data
@RequiredArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class TabCommandsEvent extends Event
{

    /**
     * Whether the event is cancelled.
     */
    private final ProxiedPlayer player;

    private final Commands commands;

    private boolean modified;

    public void clearCommands()
    {
        this.modified = true;

        this.commands.setRoot( new RootCommandNode<>() );
    }

}
