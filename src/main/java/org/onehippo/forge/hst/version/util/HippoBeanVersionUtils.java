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

import java.util.Calendar;
import java.util.List;

import javax.jcr.Credentials;
import javax.jcr.Node;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.version.Version;

import org.hippoecm.hst.container.RequestContextProvider;
import org.hippoecm.hst.content.beans.NodeAware;
import org.hippoecm.hst.content.beans.manager.ObjectBeanManager;
import org.hippoecm.hst.content.beans.manager.ObjectConverter;
import org.hippoecm.hst.content.beans.manager.ObjectConverterAware;
import org.hippoecm.hst.content.beans.standard.HippoBean;
import org.hippoecm.hst.core.request.HstRequestContext;
import org.hippoecm.hst.site.HstServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility to handle versioned, frozen node (nt:frozenNode) to covert to a HippoBean.
 * <P>
 * For example, a version is stored, embedding a frozen node which contains the versioned document variant content,
 * in locations like '/jcr:system/jcr:versionStorage/91/90/1d/91901d8f-d6ab-480a-a693-a6e459a678c3/1.0/jcr:frozenNode',
 * where '1.0' node is of 'nt:version' jcr primary type and 'jcr:frozenNode' is the node containing the versioned
 * document variant node content.
 * </P>
 */
public class HippoBeanVersionUtils {

    private static Logger log = LoggerFactory.getLogger(HippoBeanVersionUtils.class);

    private HippoBeanVersionUtils() {
    }

    /**
     * Finds the latest versioned, frozen bean as of the document.
     * @param canonicalHandlePath document handle path for which you want to retrieve a specific version
     * @param documentBeanClass mapping HST content bean class
     * @return HST content bean with versioned, frozen content
     */
    public static <T extends HippoBean> T getVersionedBeanAsOf(final String canonicalHandlePath,
                                                               final Class<T> documentBeanClass) {
        return getVersionedBeanAsOf(canonicalHandlePath, documentBeanClass, null);
    }

    /**
     * Finds a versioned, frozen bean as of the specific {@code asOf} datetime.
     * @param canonicalHandlePath document handle path for which you want to retrieve a specific version
     * @param documentBeanClass mapping HST content bean class
     * @param asOf specific datetime when the effective version should be found based on.
     * @return HST content bean with versioned, frozen content
     */
    public static <T extends HippoBean> T getVersionedBeanAsOf(final String canonicalHandlePath,
                                                               final Class<T> documentBeanClass,
                                                               final Calendar asOf) {

        T versionedBean = null;

        final HstRequestContext requestContext = RequestContextProvider.get();
        final ObjectConverter objectConverter = requestContext.getContentBeansTool().getObjectConverter();

        Session previewSession = null;

        try {
            previewSession = getPreviewSession();

            if (previewSession.nodeExists(canonicalHandlePath)) {
                Node handleNode = previewSession.getNode(canonicalHandlePath);
                final Node versionableNode = JcrVersionUtils.getVersionableNode(handleNode);

                if (versionableNode != null) {
                    List<Version> linearVersions = JcrVersionUtils.getAllLinearVersions(versionableNode);
                    Version version = JcrVersionUtils.getVersionAsOf(versionableNode, linearVersions, asOf);

                    if (version != null) {
                        if (JcrVersionUtils.ROOT_VERSION_NAME.equals(version.getName())) {
                            if (linearVersions.size() == 1) {
                                // there's no versions yet.. so simply fallback to the original document at the handle path.
                                ObjectBeanManager obm = requestContext.getObjectBeanManager();
                                versionedBean = (T) obm.getObject(canonicalHandlePath);
                            } else {
                                // New version has probably started after the initial publication by user.
                                // In this case, we cannot assume the root version to be the same of the current live variant.
                                // So, sorry, but we have to return null.. :(
                                versionedBean = null;
                            }
                        } else {
                            final Node frozenNode = version.getFrozenNode();
                            final NonFrozenPretenderNode nonFrozenPretender =
                                FrozenNodeUtils.getNonFrozenPretenderNode(frozenNode, version.getCreated(), asOf);
                            versionedBean = documentBeanClass.newInstance();

                            if (versionedBean instanceof NodeAware) {
                                ((NodeAware) versionedBean).setNode(nonFrozenPretender);
                            }

                            if (versionedBean instanceof ObjectConverterAware) {
                                ((ObjectConverterAware) versionedBean).setObjectConverter(objectConverter);
                            }
                        }
                    }
                }
            }
        } catch (RepositoryException e) {
            log.error("Failed to retrieve versioned document.", e);
        } catch (Exception e) {
            log.error("Failed to retrieve versioned document.", e);
        } finally {
            if (previewSession != null) {
                previewSession.logout();
            }
        }

        return versionedBean;
    }

    /**
     * Returns a preview JCR session.
     * <P>
     * NOTE: in most environment, preview JCR session only can read version history.
     * </P>
     * @return a preview JCR session
     * @throws RepositoryException if unexpected repository exception occurs
     */
    private static Session getPreviewSession() throws RepositoryException {
        Repository repository = HstServices.getComponentManager().getComponent(Repository.class.getName());
        Credentials previewCreds = HstServices.getComponentManager().getComponent(Credentials.class.getName() + ".preview");
        return repository.login(previewCreds);
    }

}
