/*
 * Copyright (c) 2010-2012 Grid Dynamics Consulting Services, Inc, All Rights Reserved
 * http://www.griddynamics.com
 *
 * This library is free software; you can redistribute it and/or modify it under the terms of
 * the GNU Lesser General Public License as published by the Free Software Foundation; either
 * version 2.1 of the License, or any later version.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.griddynamics.jagger.engine.e1.sessioncomparation;

import com.google.common.base.Objects;
import com.griddynamics.jagger.master.SessionIdProvider;
import org.springframework.beans.factory.annotation.Required;

public class BaselineSessionProvider {
    public static final String IDENTITY_SESSION = "#IDENTITY";

    private String sessionId;
    private SessionIdProvider sessionIdProvider;

    public String getBaselineSession() {

        if (IDENTITY_SESSION.equals(sessionId)) {
            return sessionIdProvider.getSessionId();
        }

        return sessionId;
    }

    @Required
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("sessionId", sessionId)
                .toString();
    }

    @Required
    public void setSessionIdProvider(SessionIdProvider sessionIdProvider) {
        this.sessionIdProvider = sessionIdProvider;
    }
}
