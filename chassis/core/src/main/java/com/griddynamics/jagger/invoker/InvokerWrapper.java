package com.griddynamics.jagger.invoker;

import com.griddynamics.jagger.coordinator.NodeContext;

/**
 * Created by kgribov on 2/19/14.
 */
public class InvokerWrapper<Q, R, E> {
    private Class<Invoker<Q, R, E>> invokerClazz;
    private Invoker<Q, R, E> invokerObject;

    private Invoker<Q, R, E> cachedInvoker;

    public Invoker<Q, R, E> getInvoker(NodeContext context){
        if (cachedInvoker == null){
            cachedInvoker = initInvoker(context);
        }

        return cachedInvoker;
    }

    private Invoker<Q, R, E> initInvoker(NodeContext context){
        if (invokerClazz != null){
            Invoker<Q, R, E> invoker = context.getService(invokerClazz);
            if(invoker == null) {
                throw new IllegalArgumentException("Service for class + '" + invokerClazz.getCanonicalName()
                        + "' not found!");
            }
            return context.getService(invokerClazz);
        }else{
            return invokerObject;
        }
    }

    public void setInvokerClazz(Class<Invoker<Q, R, E>> invokerClazz) {
        this.invokerClazz = invokerClazz;
    }

    public void setInvokerObject(Invoker<Q, R, E> invokerObject) {
        this.invokerObject = invokerObject;
    }
}
