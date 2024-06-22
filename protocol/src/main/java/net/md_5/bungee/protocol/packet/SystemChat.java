package net.md_5.bungee.protocol.packet;

import io.netty.buffer.ByteBuf;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import net.md_5.bungee.api.ChatMessageType;
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
public class SystemChat extends DefinedPacket
{

    private BaseComponent message;
    private int position;

    @Override
    public void read(ByteBuf buf, ProtocolConstants.Direction direction, int protocolVersion)
    {
        message = readBaseComponent( buf, 262144, protocolVersion );
        position = ( protocolVersion >= ProtocolConstants.MINECRAFT_1_19_1 ) ? ( ( buf.readBoolean() ) ? ChatMessageType.ACTION_BAR.ordinal() : 0 ) : readVarInt( buf );
    }

    @Override
    public void write(ByteBuf buf, ProtocolConstants.Direction direction, int protocolVersion)
    {
        writeBaseComponent( message, buf, protocolVersion );
        if ( protocolVersion >= ProtocolConstants.MINECRAFT_1_19_1 )
        {
            buf.writeBoolean( position == ChatMessageType.ACTION_BAR.ordinal() );
        } else
        {
            writeVarInt( position, buf );
        }
    }

    @Override
    public void handle(AbstractPacketHandler handler) throws Exception
    {
        handler.handle( this );
    }

    public void setMessage(BaseComponent message)
    {
        this.message = message;
    }

    public void setMessage(String message)
    {
        try
        {
            this.message = ComponentSerializer.deserialize( message );
        } catch ( Exception e )
        {
            this.message = new TextComponent( message );
        }
    }
}
