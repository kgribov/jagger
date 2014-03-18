package com.griddynamics.jagger.webclient.server;

import com.griddynamics.jagger.webclient.client.CommonDataService;
import com.griddynamics.jagger.webclient.client.data.WebClientProperties;

public class CommonDataServiceImpl implements CommonDataService {

    private CommonDataProvider commonDataProvider;
    private WebClientProperties webClientProperties;

    public void setWebClientProperties(WebClientProperties webClientProperties) {
        this.webClientProperties = webClientProperties;
        checkIfUserCommentAvailable();
        checkIfTagsAvailable();
    }

    @Override
    public WebClientProperties getWebClientProperties() {
        return webClientProperties;
    }

    private void checkIfUserCommentAvailable() {
        webClientProperties.setUserCommentStoreAvailable(commonDataProvider.checkIfUserCommentStorageAvailable());
    }

    private void checkIfTagsAvailable() {
        webClientProperties.setTagsStoreAvailable(commonDataProvider.checkIfTagsStorageAvailable());
    }

    public CommonDataProvider getCommonDataProvider() {
        return commonDataProvider;
    }

    public void setCommonDataProvider(CommonDataProvider commonDataProvider) {
        this.commonDataProvider = commonDataProvider;
    }
}
