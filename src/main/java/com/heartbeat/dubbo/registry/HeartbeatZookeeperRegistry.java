package com.heartbeat.dubbo.registry;

import static org.apache.dubbo.common.constants.CommonConstants.GROUP_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.PATH_SEPARATOR;
import static org.apache.dubbo.common.constants.RegistryConstants.DYNAMIC_KEY;

import java.util.HashSet;
import java.util.Set;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.constants.RegistryConstants;
import org.apache.dubbo.registry.zookeeper.ZookeeperRegistry;
import org.apache.dubbo.remoting.zookeeper.StateListener;
import org.apache.dubbo.remoting.zookeeper.ZookeeperClient;
import org.apache.dubbo.remoting.zookeeper.ZookeeperTransporter;
import org.apache.dubbo.rpc.RpcException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author yjjidt888 Date: 2020-05-25 Time: 17:53
 * @version $
 */
public class HeartbeatZookeeperRegistry extends ZookeeperRegistry {

    private static final Logger logger = LoggerFactory.getLogger(HeartbeatZookeeperRegistry.class);

    private final static String DEFAULT_ROOT = "dubbo";

    private final String root;

    private final ZookeeperClient zkClient;

    public HeartbeatZookeeperRegistry(URL url, ZookeeperTransporter zookeeperTransporter) {
        super(url, zookeeperTransporter);
        if (url.isAnyHost()) {
            throw new IllegalStateException("registry address == null");
        }
        String group = url.getParameter(GROUP_KEY, DEFAULT_ROOT);
        if (!group.startsWith(PATH_SEPARATOR)) {
            group = PATH_SEPARATOR + group;
        }
        this.root = group;
        zkClient = zookeeperTransporter.connect(url);
        zkClient.addStateListener(state -> {
            if (state == StateListener.RECONNECTED) {
                try {
                    recover();
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                }
            }
        });
    }

    private  Set<URL> urlSet = new HashSet<>();
    /**
     * 注册到dubbo
     */
    @Override
    public void doRegister(URL url) {
        //判断是否满足条件，记录可注册url
        //判断tomcat下是否有验证文件.healthcheck.html;
        //注册到zk上
        urlSet.add(url);
        try {
            zkClient.create(toUrlPath(url), url.getParameter(DYNAMIC_KEY, true));
        } catch (Throwable e) {
            throw new RpcException("Failed to register " + url + " to zookeeper " + getUrl() + ", cause: " + e.getMessage(), e);
        }
    }





    @Override
    public void doUnregister(URL url) {
        try {
            zkClient.delete(toUrlPath(url));
        } catch (Throwable e) {
            throw new RpcException(
                "Failed to unregister " + url + " to zookeeper " + getUrl() + ", cause: " + e.getMessage(), e);
        }
    }


    private String toRootDir() {
        if (root.equals(CommonConstants.PATH_SEPARATOR)) {
            return root;
        }
        return root + CommonConstants.PATH_SEPARATOR;
    }

    private String toRootPath() {
        return root;
    }

    private String toServicePath(URL url) {
        String name = url.getServiceInterface();
        if (CommonConstants.ANY_VALUE.equals(name)) {
            return toRootPath();
        }
        return toRootDir() + URL.encode(name);
    }


    private String toCategoryPath(URL url) {
        return toServicePath(url) + CommonConstants.PATH_SEPARATOR + url
            .getParameter(RegistryConstants.CATEGORY_KEY, RegistryConstants.DEFAULT_CATEGORY);
    }

    private String toUrlPath(URL url) {
        return toCategoryPath(url) + CommonConstants.PATH_SEPARATOR + URL.encode(url.toFullString());
    }

}
