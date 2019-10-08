package io.coti.trustscore.services;

import io.coti.basenode.crypto.NodeCryptoHelper;
import io.coti.basenode.data.*;
import io.coti.basenode.data.interfaces.IPropagatable;
import io.coti.basenode.exceptions.CotiRunTimeException;
import io.coti.basenode.services.BaseNodeInitializationService;
import io.coti.basenode.services.interfaces.ICommunicationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;

@Service
@Slf4j
public class InitializationService extends BaseNodeInitializationService {

    @Autowired
    private ICommunicationService communicationService;
    @Value("${server.port}")
    private String serverPort;
    @Value("${server.url}")
    private String webServerUrl;
    private EnumMap<NodeType, List<Class<? extends IPropagatable>>> publisherNodeTypeToMessageTypesMap = new EnumMap<>(NodeType.class);

    @PostConstruct
    public void init() {
        try {
            super.init();
            super.initDB();
            super.createNetworkNodeData();
            super.getNetwork();

            publisherNodeTypeToMessageTypesMap.put(NodeType.ZeroSpendServer, Arrays.asList(TransactionData.class, DspConsensusResult.class, InitiatedTokenNoticeData.class));
            publisherNodeTypeToMessageTypesMap.put(NodeType.FinancialServer, Arrays.asList(TransactionData.class));

            communicationService.initSubscriber(NodeType.TrustScoreNode, publisherNodeTypeToMessageTypesMap);

            NetworkNodeData zerospendNetworkNodeData = networkService.getSingleNodeData(NodeType.ZeroSpendServer);
            if (zerospendNetworkNodeData == null) {
                log.error("No zerospend server exists in the network got from the node manager. Exiting from the application");
                System.exit(SpringApplication.exit(applicationContext));
            }
            networkService.setRecoveryServerAddress(zerospendNetworkNodeData.getHttpFullAddress());
            communicationService.addSubscription(zerospendNetworkNodeData.getPropagationFullAddress(), NodeType.ZeroSpendServer);
            networkService.addListToSubscription(networkService.getMapFromFactory(NodeType.DspNode).values());
            if (networkService.getSingleNodeData(NodeType.FinancialServer) != null) {
                networkService.addListToSubscription(new ArrayList<>(Arrays.asList(networkService.getSingleNodeData(NodeType.FinancialServer))));
            }

            super.initServices();
        } catch (Exception e) {
            log.error("Errors at {}", this.getClass().getSimpleName());
            if (e instanceof CotiRunTimeException) {
                ((CotiRunTimeException) e).logMessage();
            } else {
                log.error("{}: {}", e.getClass().getName(), e.getMessage());
            }
            System.exit(SpringApplication.exit(applicationContext));
        }
    }

    @Override
    protected NetworkNodeData createNodeProperties() {
        NetworkNodeData networkNodeData = new NetworkNodeData(NodeType.TrustScoreNode, nodeIp, serverPort, NodeCryptoHelper.getNodeHash(), networkType);
        networkNodeData.setWebServerUrl(webServerUrl);
        return networkNodeData;

    }
}