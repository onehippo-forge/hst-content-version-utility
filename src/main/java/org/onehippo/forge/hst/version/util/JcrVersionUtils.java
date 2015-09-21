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
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.version.Version;
import javax.jcr.version.VersionHistory;
import javax.jcr.version.VersionIterator;
import javax.jcr.version.VersionManager;

import org.apache.commons.lang.StringUtils;
import org.hippoecm.repository.HippoStdNodeType;
import org.hippoecm.repository.api.HippoNodeType;
import org.hippoecm.repository.util.JcrUtils;

/**
 * JCR Version Utilities.
 */
public class JcrVersionUtils {

    /**
     * Root version name.
     */
    public static final String ROOT_VERSION_NAME = "jcr:rootVersion";

    private JcrVersionUtils() {
    }

    /**
     * Returns the versionable node (which is the same as preview variant node in case of hippo document handle).
     * If {@code handleNode} is not a hippo:handle or a mix:versionable, then it returns null.
     * @param handleNode document handle node
     * @return the versionable document variant node (which is the same as preview variant node)
     * @throws RepositoryException if unexpected repository exception occurs
     */
    public static Node getVersionableNode(final Node handleNode) throws RepositoryException {
        if (!handleNode.isNodeType(HippoNodeType.NT_HANDLE)) {
            if (handleNode.isNodeType("mix:versionable")) {
                return handleNode;
            } else {
                return null;
            }
        }

        Node versionableNode = null;

        String variantState;
        Node variantNode;

        for (NodeIterator nodeIt = handleNode.getNodes(handleNode.getName()); nodeIt.hasNext();) {
            variantNode = nodeIt.nextNode();

            if (variantNode != null && variantNode.isNodeType(HippoStdNodeType.NT_PUBLISHABLE)) {
                variantState = variantNode.getProperty(HippoStdNodeType.HIPPOSTD_STATE).getString();

                if (HippoStdNodeType.UNPUBLISHED.equals(variantState)) {
                    versionableNode = variantNode;
                    break;
                }
            }
        }

        return versionableNode;
    }

    /**
     * Finds the root version of the {@code versionableNode} from the given {@code linearVersions}.
     * @param versionableNode versionable node (type of mix:versionable)
     * @param linearVersions linear versions list
     * @return the root version of the {@code versionableNode}
     * @throws RepositoryException if unexpected repository exception occurs
     */
    public static Version getRootVersion(final Node versionableNode, final List<Version> linearVersions)
            throws RepositoryException {
        Version rootVersion = null;

        for (Version version : linearVersions) {
            if (ROOT_VERSION_NAME.equals(version.getName())) {
                rootVersion = version;
                break;
            }
        }

        return rootVersion;
    }

    /**
     * Finds the latest version of the {@code versionableNode} as of {@code asOf} datetime
     * from the given {@code linearVersions}.
     * If {@code asOf} is null, then returns the latest version.
     * @param versionableNode versionable node (type of mix:versionable)
     * @param linearVersions linear versions list
     * @param asOf {@code asOf} datetime
     * @return the latest version of the {@code versionableNode} as of {@code asOf} datetime
     *         If {@code asOf} is null, then returns the latest version.
     * @throws RepositoryException if unexpected repository exception occurs
     */
    public static Version getVersionAsOf(final Node versionableNode, final List<Version> linearVersions,
            final Calendar asOf) throws RepositoryException {
        Version asOfVersion = null;

        if (asOf == null) {
            return getRootVersion(versionableNode, linearVersions);
        } else {
            for (Version version : linearVersions) {
                if (hasLiveFrozenNode(version)) {
                    Calendar created = version.getCreated();
                    int compare = created.compareTo(asOf);

                    if (compare <= 0) {
                        asOfVersion = version;
                    } else {
                        break;
                    }
                }
            }
        }

        return asOfVersion;
    }

    /**
     * Finds all ordered linear versions of the {@code versionableNode}.
     * @param versionableNode versionable node (type of mix:versionable).
     * @return list of {@link Version} instances
     * @throws RepositoryException if unexpected repository exception occurs
     */
    public static List<Version> getAllLinearVersions(final Node versionableNode) throws RepositoryException {
        List<Version> versions = null;

        VersionManager versionManager = versionableNode.getSession().getWorkspace().getVersionManager();
        VersionHistory versionHistory = versionManager.getVersionHistory(versionableNode.getPath());

        if (versionHistory != null) {
            VersionIterator versionIt = versionHistory.getAllLinearVersions();
            long size = versionIt.getSize();

            if (size > 0 || size == -1) {
                versions = new LinkedList<>();
                Version version;

                while (versionIt.hasNext()) {
                    version = versionIt.nextVersion();
                    versions.add(version);
                }
            }
        }

        if (versions == null) {
            versions = Collections.emptyList();
        }

        return versions;
    }

    /**
     * Returns true if the {@code version} has a frozen node which is the content of the "live" unpublished document.
     * @param version version
     * @return true if the {@code version} has a frozen node which is the content of the "live" unpublished document
     * @throws RepositoryException if unexpected repository exception occurs
     */
    public static boolean hasLiveFrozenNode(final Version version) throws RepositoryException {
        final Node frozenNode = version.getFrozenNode();

        if (frozenNode == null) {
            return false;
        }

        if (frozenNode.hasProperty(HippoStdNodeType.HIPPOSTD_STATESUMMARY)) {
            String stateSummary = JcrUtils.getStringProperty(frozenNode, HippoStdNodeType.HIPPOSTD_STATESUMMARY, null);
            return StringUtils.equals("live", stateSummary);
        }

        return false;
    }
}
