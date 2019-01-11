/*
 *  Copyright 2015-2019 Hippo B.V. (http://www.onehippo.com)
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Calendar;

import javax.jcr.Node;

import org.junit.Before;
import org.junit.Test;
import org.onehippo.repository.mock.MockNode;

public class FrozenNodeUtilsTest {

    private MockNode frozenNode;
 
    @Before
    public void before() throws Exception {
        MockNode versionStorage = MockNode.root().addNode("versionStorage1", "rep:versionStorage");
        MockNode versionHistory = versionStorage.addNode("versionHistory1", "nt:versionHistory");
        MockNode version = versionHistory.addNode("1.0", "nt:version");

        frozenNode = version.addNode("jcr:frozenNode", "nt:frozenNode");
        frozenNode.setProperty("jcr:frozenPrimaryType", "ns1:service");
        frozenNode.setProperty("ns1:title", "MRI");
        frozenNode.setProperty("ns1:variableid", "mri");

        assertTrue(frozenNode.isNodeType("nt:frozenNode"));
        assertFalse(frozenNode.isNodeType("ns1:service"));
        assertEquals("nt:frozenNode", frozenNode.getPrimaryNodeType().getName());

        MockNode description = frozenNode.addNode("ns1:description", "nt:frozenNode");
        description.setProperty("jcr:frozenPrimaryType", "hippostd:html");
        description.setProperty("hippostd:content", "<p>Magnetic Resonance imaging</p>");

        assertTrue(description.isNodeType("nt:frozenNode"));
        assertFalse(description.isNodeType("hippostd:html"));
        assertEquals("nt:frozenNode", description.getPrimaryNodeType().getName());
    }

    @Test
    public void testPretendNotFrozen() throws Exception {
        Calendar now = Calendar.getInstance();
        NonFrozenPretenderNode pretenderNode = FrozenNodeUtils.getNonFrozenPretenderNode(frozenNode, now, now);

        assertSame(frozenNode, pretenderNode.getFrozenNode());
        assertEquals(now, pretenderNode.getCreated());
        assertEquals(now, pretenderNode.getAsOf());

        assertTrue(pretenderNode.isNodeType("nt:frozenNode"));
        assertTrue(pretenderNode.isNodeType("ns1:service"));
        assertEquals("ns1:service", pretenderNode.getPrimaryNodeType().getName());

        assertEquals("MRI", pretenderNode.getProperty("ns1:title").getString());
        assertEquals("mri", pretenderNode.getProperty("ns1:variableid").getString());

        assertTrue(pretenderNode.hasNode("ns1:description"));
        Node descriptionNode = pretenderNode.getNode("ns1:description");
        assertTrue(descriptionNode instanceof NonFrozenPretenderNode);

        NonFrozenPretenderNode pretenderDescriptionNode = (NonFrozenPretenderNode) descriptionNode;
        assertSame(frozenNode.getNode("ns1:description"), pretenderDescriptionNode.getFrozenNode());
        assertTrue(pretenderDescriptionNode.isNodeType("nt:frozenNode"));
        assertTrue(pretenderDescriptionNode.isNodeType("hippostd:html"));
        assertEquals("<p>Magnetic Resonance imaging</p>",
                     pretenderDescriptionNode.getProperty("hippostd:content").getString());
    }

}
