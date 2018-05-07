package com.saaavsaaa.client.cache;

import com.saaavsaaa.client.utility.PathUtil;
import com.saaavsaaa.client.utility.constant.Constants;
import com.saaavsaaa.client.utility.section.ClientTask;
import com.saaavsaaa.client.utility.section.Listener;
import com.saaavsaaa.client.utility.section.Properties;
import com.saaavsaaa.client.zookeeper.Provider;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.common.PathUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Created by aaa
 */
public final class PathTree {
    private ScheduledExecutorService cacheService;
    private final Provider provider;
    private PathNode rootNode;
    private PathStatus Status;
    
    public PathTree(final String root, final Provider provider) {
        this.rootNode = new PathNode(root);
        this.Status = PathStatus.RELEASE;
        this.provider = provider;
    }
    
    public synchronized void loading() throws KeeperException, InterruptedException {
        if (Status == Status.RELEASE){
            this.setStatus(PathStatus.CHANGING);
    
            PathNode newRoot = new PathNode(rootNode.getKey());
            List<String> children = provider.getChildren(rootNode.getKey());
            children.remove(provider.getRealPath(Constants.CHANGING_KEY));
            this.attechIntoNode(children, newRoot, provider);
            rootNode = newRoot;
    
            this.setStatus(PathStatus.RELEASE);
        } else {
            try {
                Thread.sleep(10L);
            } catch (InterruptedException e) {
                System.out.println("cache put status not release");
            }
            loading();
        }
    }
    
    private void attechIntoNode(final List<String> children, final PathNode pathNode, final Provider provider) throws KeeperException, InterruptedException {
        if (children.isEmpty()){
            return;
        }
        for (String child : children) {
            String childPath = PathUtil.getRealPath(pathNode.getKey(), child);
            PathNode current = new PathNode(PathUtil.checkPath(child), provider.getData(childPath));
            pathNode.attachChild(current);
            List<String> subs = provider.getChildren(childPath);
            this.attechIntoNode(subs, current, provider);
        }
    }
    
    public void refreshPeriodic(final long period){
        long threadPeriod = period;
        if (threadPeriod < 1){
            threadPeriod = Properties.INSTANCE.getThreadPeriod();
        }
        cacheService = Executors.newSingleThreadScheduledExecutor();
        cacheService.scheduleAtFixedRate(new ClientTask(provider) {
            @Override
            public void run(Provider provider) throws KeeperException, InterruptedException {
                if (PathStatus.RELEASE == getStatus()) {
                    loading();
                }
            }
        }, Properties.INSTANCE.getThreadInitialDelay(), threadPeriod, TimeUnit.MILLISECONDS);
    }
    
    public void watch(final Listener listener){
        provider.watch(rootNode.getKey(), listener);
    }
    
    public PathStatus getStatus() {
        return Status;
    }
    
    public void setStatus(final PathStatus status) {
        Status = status;
    }
    
    public PathNode getRootNode() {
        return rootNode;
    }
    
    public byte[] getValue(final String path){
        PathNode node = get(path);
        return null == node ? null : node.getValue();
    }
    
    private Iterator<String> keyIterator(final String path){
        List<String> nodes = PathUtil.getShortPathNodes(path);
        Iterator<String> iterator = nodes.iterator();
        iterator.next(); // root
        return iterator;
    }
    
    public List<String> getChildren(String path) {
        PathNode node = get(path);
        List<String> result = new ArrayList<>();
        if (node == null){
            return result;
        }
        if (node.getChildren().isEmpty()) {
            return result;
        }
        Iterator<PathNode> children = node.getChildren().values().iterator();
        while (children.hasNext()){
            result.add(new String(children.next().getValue()));
        }
        return result;
    }
    
    private PathNode get(final String path){
        PathUtils.validatePath(path);
        if (path.equals(rootNode.getKey())){
            return rootNode;
        }
        Iterator<String> iterator = keyIterator(path);
        if (iterator.hasNext()) {
            return rootNode.get(iterator); //rootNode.get(1, path);
        }
        return null;
    }
    
    public synchronized void put(final String path, final String value) {
        PathUtils.validatePath(path);
        if (Status == Status.RELEASE){
            if (path.equals(rootNode.getKey())){
                rootNode.setValue(value.getBytes(Constants.UTF_8));
                return;
            }
            this.setStatus(PathStatus.CHANGING);
            rootNode.set(keyIterator(path), value);
            this.setStatus(PathStatus.RELEASE);
        } else {
            try {
                Thread.sleep(10L);
            } catch (InterruptedException e) {
                System.out.println("cache put status not release");
            }
            put(path, value);
        }
    }
    
    public synchronized void delete(String path) {
        PathUtils.validatePath(path);
        String prxpath = path.substring(0, path.lastIndexOf(Constants.PATH_SEPARATOR));
        PathNode node = get(prxpath);
        node.getChildren().remove(path);
    }
}
