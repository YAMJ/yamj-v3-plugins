package org.yamj.plugin.ofdb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamj.plugin.api.YamjPlugin;
import ro.fortsoft.pf4j.PluginWrapper;

public class OfdbPlugin extends YamjPlugin {
    
    private static final Logger LOG = LoggerFactory.getLogger(OfdbPlugin.class);

    public OfdbPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public void start() {
        LOG.trace("Start OfdbPlugin");
    }

    @Override
    public void stop() {
        LOG.trace("Stop OfdbPlugin");
    }
}