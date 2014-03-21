package com.griddynamics.jagger.webclient.client.components.control.model;

import java.util.Collections;
import java.util.List;

/**
 * User: amikryukov
 * Date: 11/26/13
 */
public class SessionInfoNode extends AbstractIdentifyNode {

    public SessionInfoNode() {
    }

    public SessionInfoNode(String id, String displayName) {
        super(id, displayName);
    }

    @Override
    public List<? extends AbstractIdentifyNode> getChildren() {
        return Collections.EMPTY_LIST;
    }
}
