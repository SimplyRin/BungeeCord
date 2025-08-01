package net.md_5.bungee.protocol.packet;

import io.netty.buffer.ByteBuf;
import java.util.Locale;
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
import net.md_5.bungee.protocol.data.NumberFormat;
import net.md_5.bungee.protocol.util.Either;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class ScoreboardObjective extends DefinedPacket
{

    private String name;
    private Either<String, BaseComponent> value;
    private HealthDisplay type;
    /**
     * 0 to create, 1 to remove, 2 to update display text.
     */
    private byte action;
    private NumberFormat numberFormat;

    @Override
    public void read(ByteBuf buf, ProtocolConstants.Direction direction, int protocolVersion)
    {
        name = readString( buf );
        action = buf.readByte();
        if ( action == 0 || action == 2 )
        {
            if ( protocolVersion >= ProtocolConstants.MINECRAFT_1_13 )
            {
                value = readEitherBaseComponent( buf, protocolVersion, false );
                type = HealthDisplay.values()[readVarInt( buf )];
            } else
            {
                value = readEitherBaseComponent( buf, protocolVersion, true );
                type = HealthDisplay.fromString( readString( buf ) );
            }
            if ( protocolVersion >= ProtocolConstants.MINECRAFT_1_20_3 )
            {
                numberFormat = readNullable( (b) -> readNumberFormat( b, protocolVersion ), buf );
            }
        }
    }

    @Override
    public void write(ByteBuf buf, ProtocolConstants.Direction direction, int protocolVersion)
    {
        writeString( name, buf );
        buf.writeByte( action );
        if ( action == 0 || action == 2 )
        {
            writeEitherBaseComponent( value, buf, protocolVersion );
            if ( protocolVersion >= ProtocolConstants.MINECRAFT_1_13 )
            {
                writeVarInt( type.ordinal(), buf );
            } else
            {
                writeString( type.toString(), buf );
            }
            if ( protocolVersion >= ProtocolConstants.MINECRAFT_1_20_3 )
            {
                writeNullable( numberFormat, (s, b) -> DefinedPacket.writeNumberFormat( s, b, protocolVersion ), buf );
            }
        }
    }

    @Override
    public void handle(AbstractPacketHandler handler) throws Exception
    {
        handler.handle( this );
    }

    public enum HealthDisplay
    {

        INTEGER, HEARTS;

        @Override
        public String toString()
        {
            return super.toString().toLowerCase( Locale.ROOT );
        }

        public static HealthDisplay fromString(String s)
        {
            return valueOf( s.toUpperCase( Locale.ROOT ) );
        }
    }

    public void setValue(Either<String, BaseComponent> value)
    {
        this.value = value;
    }

    public void setValue(String value)
    {
        try
        {
            this.value = new Either<>( value, ComponentSerializer.deserialize( value ) );
        } catch ( Exception e )
        {
            this.value = new Either<>( value, new TextComponent( value ) );
        }
    }
}
