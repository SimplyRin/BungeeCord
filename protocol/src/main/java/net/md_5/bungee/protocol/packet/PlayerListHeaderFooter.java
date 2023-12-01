package net.md_5.bungee.protocol.packet;

import io.netty.buffer.ByteBuf;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.chat.ComponentSerializer;
import net.md_5.bungee.protocol.AbstractPacketHandler;
import net.md_5.bungee.protocol.DefinedPacket;
import net.md_5.bungee.protocol.ProtocolConstants;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class PlayerListHeaderFooter extends DefinedPacket
{

    private BaseComponent header;
    private BaseComponent footer;

    public PlayerListHeaderFooter(String header, String footer)
    {
        try
        {
            this.header = ComponentSerializer.deserialize( header );
        } catch ( Exception e )
        {
            this.header = new TextComponent( header );
        }
        try
        {
            this.footer = ComponentSerializer.deserialize( footer );
        } catch ( Exception e )
        {
            this.footer = new TextComponent( footer );
        }
    }

    @Override
    public void read(ByteBuf buf, ProtocolConstants.Direction direction, int protocolVersion)
    {
        header = readBaseComponent( buf, protocolVersion );
        footer = readBaseComponent( buf, protocolVersion );
    }

    @Override
    public void write(ByteBuf buf, ProtocolConstants.Direction direction, int protocolVersion)
    {
        writeBaseComponent( header, buf, protocolVersion );
        writeBaseComponent( footer, buf, protocolVersion );
    }

    @Override
    public void handle(AbstractPacketHandler handler) throws Exception
    {
        handler.handle( this );
    }
}
