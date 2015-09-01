/*
 *  Copyright 2015-2015 Hippo B.V. (http://www.onehippo.com)
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.onehippo.forge.hst.version.util;

import java.lang.reflect.Method;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.nodetype.NodeType;

import org.apache.commons.proxy.Interceptor;
import org.apache.commons.proxy.Invocation;
import org.apache.commons.proxy.ProxyFactory;
import org.apache.jackrabbit.commons.iterator.NodeIteratorAdapter;
import org.hippoecm.repository.api.HippoNode;
import org.hippoecm.repository.api.Localized;

/**
 * Utility to handle versioned, frozen node (nt:frozenNode).
 * <P>
 * For example, a version is stored, embedding a frozen node which contains the versioned document variant content,
 * in locations like '/jcr:system/jcr:versionStorage/91/90/1d/91901d8f-d6ab-480a-a693-a6e459a678c3/1.0/jcr:frozenNode',
 * where '1.0' node is of 'nt:version' jcr primary type and 'jcr:frozenNode' is the node containing the versioned
 * document variant node content.
 * </P>
 * <P>
 * Note that HST Content Beans module doesn't allow to map between beans and frozen nodes by default.
 * Therefore, this utility class provides a way to proxy a frozen node instance to pretend as a non-frozen node instance.
 * </P>
 */
public class FrozenNodeUtils {

    /**
     * Classes to create a proxy instance for the {@link NodeType} of a frozen node.
     */
    private static final Class [] NON_FROZEN_PRETENDER_NODE_TYPE_CLASSES = { NodeType.class };

    /**
     * Classes to create a proxy instance for a frozen node.
     */
    private static final Class [] NON_FROZEN_PRETENDER_NODE_CLASSES = { NonFrozenPretenderNode.class };

    /**
     * Classes to create a proxy instance for a frozen hippo node.
     */
    private static final Class [] NON_FROZEN_PRETENDER_HIPPO_NODE_CLASSES = { NonFrozenPretenderHippoNode.class };

    private FrozenNodeUtils() {
    }

    /**
     * Returns a proxy which pretends to be non-frozen node from the {@code frozenNode}.
     * @param frozenNode frozen node
     * @param created created date time
     * @param asOf as-of date time
     * 
     * @return a proxy which pretends to be non-frozen node from the {@code frozenNode}
     * @throws RepositoryException if unexpected repository exception occurs
     */
    public static NonFrozenPretenderNode getNonFrozenPretenderNode(final Node frozenNode, final Calendar created, final Calendar asOf) throws RepositoryException {

        if (!frozenNode.isNodeType("nt:frozenNode")) {
            throw new IllegalArgumentException("frozenNode must be type of nt:frozenNode!");
        }

        ProxyFactory proxyFactory = new ProxyFactory();

        final Interceptor primaryNodeTypeInterceptor = new Interceptor() {
            @Override
            public Object intercept(Invocation invocation) throws Throwable {
                final Method method = invocation.getMethod();
                final String methodName = method.getName();

                if ("getName".equals(methodName)) {
                    return frozenNode.getProperty("jcr:frozenPrimaryType").getString();
                }

                return invocation.proceed();
            }
        };

        final NodeType primaryNodeTypeProxy =
            (NodeType) proxyFactory.createInterceptorProxy(frozenNode.getPrimaryNodeType(),
                                                           primaryNodeTypeInterceptor,
                                                           NON_FROZEN_PRETENDER_NODE_TYPE_CLASSES);

        final Interceptor nodeInterceptor = new Interceptor() {
            @Override
            public Object intercept(Invocation invocation) throws Throwable {
                final Method method = invocation.getMethod();
                final Class<?> declaringClass = method.getDeclaringClass();
                final String methodName = method.getName();
                final Object [] arguments = invocation.getArguments();

                if (Node.class.equals(declaringClass)) {
                    if ("getPrimaryNodeType".equals(methodName)) {
                        return primaryNodeTypeProxy;
                    } else if ("isNodeType".equals(methodName)) {
                        return proxyIsNodeType(frozenNode, created, asOf, (String) arguments[0]);
                    } else if ("getNode".equals(methodName)) {
                        return proxyGetNode(frozenNode, created, asOf, (String) arguments[0]);
                    } else if ("getNodes".equals(methodName)) {
                        if (arguments == null || arguments.length == 0) {
                            return proxyGetNodes(frozenNode, created, asOf);
                        } else if (arguments[0] != null && arguments[0].getClass().isArray()) {
                            return proxyGetNodes(frozenNode, created, asOf, (String []) arguments[0]);
                        } else {
                            return proxyGetNodes(frozenNode, created, asOf, (String) arguments[0]);
                        }
                    }
                } else if (HippoNode.class.equals(declaringClass)) {
                    if ("getLocalizedName".equals(methodName)) {
                        return frozenNode.getName();
                    } else if ("getLocalizedNames".equals(methodName)) {
                        final Map<Localized, String> names = new HashMap<>();
                        names.put(Localized.getInstance(), frozenNode.getName());
                        names.put(Localized.getInstance(Locale.getDefault()), frozenNode.getName());
                        return names;
                    } else if ("getCanonicalNode".equals(methodName)) {
                        return frozenNode;
                    } else if ("pendingChanges".equals(methodName)) {
                        return new EmptyNodeIterator();
                    } else if ("isVirtual".equals(methodName)) {
                        return false;
                    } else if ("recomputeDerivedData".equals(methodName)) {
                        return null;
                    }
                } else if (NonFrozenPretenderNode.class.equals(declaringClass)) {
                    if ("getFrozenNode".equals(methodName)) {
                        return frozenNode;
                    } else if ("getCreated".equals(methodName)) {
                        return created;
                    } else if ("getAsOf".equals(methodName)) {
                        return asOf;
                    }
                }

                return invocation.proceed();
            }
        };

        NonFrozenPretenderNode pretenderNode = null;

        if (frozenNode instanceof HippoNode) {
            pretenderNode = (NonFrozenPretenderNode) proxyFactory
                .createInterceptorProxy(frozenNode, nodeInterceptor, NON_FROZEN_PRETENDER_HIPPO_NODE_CLASSES);
        } else {
            pretenderNode = (NonFrozenPretenderNode) proxyFactory
                .createInterceptorProxy(frozenNode, nodeInterceptor, NON_FROZEN_PRETENDER_NODE_CLASSES);
        }

        return pretenderNode;
    }

    private static boolean proxyIsNodeType(final Node frozenNode, final Calendar created, final Calendar asOf, final String nodeType) throws RepositoryException {
        if (frozenNode.isNodeType(nodeType)) {
            return true;
        }

        String frozenType = frozenNode.getProperty("jcr:frozenPrimaryType").getString();

        if (nodeType.equals(frozenType)) {
            return true;
        }

        for (NodeType mixinType : frozenNode.getMixinNodeTypes()) {
            frozenType = mixinType.getName();

            if (nodeType.equals(frozenType)) {
                return true;
            }
        }

        return false;
    }

    private static Node proxyGetNode(final Node frozenNode, final Calendar created, final Calendar asOf, final String relPath) throws RepositoryException {
        Node childFrozenNode = frozenNode.getNode(relPath);
        Node pretendingChild = getNonFrozenPretenderNode(childFrozenNode, created, asOf);
        return pretendingChild;
    }

    private static NodeIterator proxyGetNodes(final Node frozenNode, final Calendar created, final Calendar asOf) throws RepositoryException {
        List<Node> childNodes = new LinkedList<>();
        Node childNode;

        for (NodeIterator nodeIt = frozenNode.getNodes(); nodeIt.hasNext(); ) {
            childNode = nodeIt.nextNode();

            if (childNode != null) {
                childNode = getNonFrozenPretenderNode(childNode, created, asOf);
                childNodes.add(childNode);
            }
        }

        return new NodeIteratorAdapter(childNodes);
    }

    private static NodeIterator proxyGetNodes(final Node frozenNode, final Calendar created, final Calendar asOf, final String namePattern) throws RepositoryException {
        List<Node> childNodes = new LinkedList<>();
        Node childNode;

        for (NodeIterator nodeIt = frozenNode.getNodes(namePattern); nodeIt.hasNext(); ) {
            childNode = nodeIt.nextNode();

            if (childNode != null) {
                childNode = getNonFrozenPretenderNode(childNode, created, asOf);
                childNodes.add(childNode);
            }
        }

        return new NodeIteratorAdapter(childNodes);
    }

    private static NodeIterator proxyGetNodes(final Node frozenNode, final Calendar created, final Calendar asOf, final String [] nameGlobs) throws RepositoryException {
        List<Node> childNodes = new LinkedList<>();
        Node childNode;

        for (NodeIterator nodeIt = frozenNode.getNodes(nameGlobs); nodeIt.hasNext(); ) {
            childNode = nodeIt.nextNode();

            if (childNode != null) {
                childNode = getNonFrozenPretenderNode(childNode, created, asOf);
                childNodes.add(childNode);
            }
        }

        return new NodeIteratorAdapter(childNodes);
    }

    private static class EmptyNodeIterator implements NodeIterator {

        @Override
        public void skip(long skipNum) {
        }

        @Override
        public long getSize() {
            return 0;
        }

        @Override
        public long getPosition() {
            return 0;
        }

        @Override
        public boolean hasNext() {
            return false;
        }

        @Override
        public Object next() {
            return null;
        }

        @Override
        public void remove() {
        }

        @Override
        public Node nextNode() {
            return null;
        }
    }
}
