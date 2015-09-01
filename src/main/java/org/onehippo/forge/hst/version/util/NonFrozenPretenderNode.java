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

import javax.jcr.Node;

/**
 * JCR Node abstraction which extends {@link Node} and
 * pretends to be a non-frozen node.
 */
public interface NonFrozenPretenderNode extends Node {

    /**
     * Returns the wrapped frozen node.
     * @return
     */
    public Node getFrozenNode();

    /**
     * Returns the date when this frozen node in the specific version was created.
     *
     * @return a <code>Calendar</code> object
     */
    public Calendar getCreated();

    /**
     * Returns the date when this frozen node was selected as the latest effective content based on.
     * @return
     */
    public Calendar getAsOf();

}
