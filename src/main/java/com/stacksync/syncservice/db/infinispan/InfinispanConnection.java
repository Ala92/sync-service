package com.stacksync.syncservice.db.infinispan;

import com.stacksync.syncservice.db.Connection;
import org.infinispan.atomic.AtomicObjectFactory;
import org.infinispan.commons.api.BasicCache;

/**
 *
 * @author Laura Martínez Sanahuja <lauramartinezsanahuja@gmail.com>
 */
public class InfinispanConnection implements Connection {
    
    private AtomicObjectFactory factory;
    
    public InfinispanConnection(BasicCache cache) {
        this.factory = new AtomicObjectFactory(cache,0);
    }
    
    public AtomicObjectFactory getFactory() {
        return this.factory;
    }
    
    @Override
    public void setAutoCommit(boolean autoCommit) throws Exception { }

    @Override
    public void commit() throws Exception { }

    @Override
    public void rollback() throws Exception { }

    @Override
    public void close() throws Exception { }
    
    
    
}
