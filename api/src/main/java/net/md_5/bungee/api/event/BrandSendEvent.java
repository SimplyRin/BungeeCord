package net.md_5.bungee.api.event;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import net.md_5.bungee.api.plugin.Event;

@Getter
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class BrandSendEvent extends Event
{

    // UserConnection
    private final Object userConnection;

    private boolean overwrite;

    private String brand;

    private final String bungeeBrand;

    private final String bukkitBrand;

    public void setBrand(String brand)
    {
        this.brand = brand;
        this.overwrite = true;
    }

}
