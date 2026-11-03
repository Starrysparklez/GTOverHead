package dev.fluery.gtoverhead.compat.jade;

import dev.fluery.gtoverhead.block.ConnectorBlock;
import dev.fluery.gtoverhead.block.RelayConnectorBlock;

import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Jade integration. Soft dependency: this class is only loaded by Jade itself
 * (via the WailaPlugin annotation), so the mod works fine without Jade.
 */
@WailaPlugin
public class GTOverheadJadePlugin implements IWailaPlugin {

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        var provider = new ConnectorJadeProvider();
        registration.registerBlockComponent(provider, ConnectorBlock.class);
        registration.registerBlockComponent(provider, RelayConnectorBlock.class);
    }
}
