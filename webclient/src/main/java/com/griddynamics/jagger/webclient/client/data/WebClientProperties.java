package com.griddynamics.jagger.webclient.client.data;

import java.io.Serializable;

public class WebClientProperties implements Serializable {

    private boolean userCommentAvailable = false;
    private int userCommentMaxLength = 1000;

    public boolean isUserCommentAvailable() {
        return userCommentAvailable;
    }

    public void setUserCommentAvailable(boolean userCommentAvailable) {
        this.userCommentAvailable = userCommentAvailable;
    }

    public int getUserCommentMaxLength() {
        return userCommentMaxLength;
    }

    public void setUserCommentMaxLength(int userCommentMaxLength) {
        this.userCommentMaxLength = userCommentMaxLength;
    }
}
