/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2018 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2018 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.oce.model.impl;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

import org.opennms.oce.model.api.Model;
import org.opennms.oce.model.api.ModelObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModelImpl implements Model {

    private static final Logger LOG = LoggerFactory.getLogger(ModelImpl.class);
    private final Map<String, Map<String, ModelObject>> mosByTypeAndById = new HashMap<>();
    private final ModelObject root;

    public ModelImpl(ModelObject root) {
        this.root = Objects.requireNonNull(root);
        // Index the tree
        index(root);
    }

    @Override
    public ModelObject getObjectById(String type, String id) {
        return mosByTypeAndById.getOrDefault(type, Collections.emptyMap()).get(id);
    }

    @Override
    public Map<String, ModelObject> getObjectsByIdForType(String type) {
        return mosByTypeAndById.get(type);
    }

    @Override
    public Set<String> getTypes() {
        return mosByTypeAndById.keySet();
    }

    @Override
    public ModelObject getRoot() {
        return root;
    }

    @Override
    public int getSize() {
        //TODO efficiently
        return 0;
    }

    @Override
    public void printModel() {

        Queue<ModelObject> q = new LinkedList<>();
        LOG.info("Model is:");


        q.add(root);
        while(!q.isEmpty()) {
            int levelSize = q.size();
            for(int i = 0; i < levelSize; i++) {
                ModelObject currNode = q.poll();

                LOG.info(currNode.toString());

                for(ModelObject someChild : currNode.getChildren()) {
                    q.add(someChild);
                }
            }
        }
    }

    @Override
    public void addObject(String type, String id, String parentType, String parentId) {

    }

    @Override
    public void addPeerRelation(String type, String id, String typePeer1, String idPeer1, String typePeer2, String idPeer2) {

    }

    @Override
    public void addRelativeRelation(String type, String id, String relativeType, String relativeId) {

    }

    /**
     * Decouple abstraction from implementation
     */
    private void addObject(ModelObjectImpl mo, ModelObject parent) {
        String type = mo.getType();
        if(getObjectById(type, mo.getId()) != null) {
            throw new IllegalStateException("Object " + mo.getId() + " with type " + type + " already exists '");
        }

        //checking if this is a new type, if yes, create new typ
        if(mosByTypeAndById.get(type) == null) {
            mosByTypeAndById.put(type, new HashMap<>());
        }

        //if there is no parent we assume that this is top level network element
        if(parent == null) {
            mo.setParent(root);
        } else {
            mo.setParent(parent);
        }

        Map<String, ModelObject> typeMap = mosByTypeAndById.get(type);
        typeMap.put(mo.getId(), mo);

        if(type.equals("Device") || type.equals("Card") || type.equals("Port")) {
            addParentChildRel(mo);
        }
        else if(type.equals("Link")) {
            Set<ModelObject> peers = mo.getPeers();
            addPeerRel(mo, peers);
        }
        //TODO
        // Check level of hierarchy
        // -- If it is mid level (card), attach to the parent (check if parent exists)
    }

    private void addParentChildRel(ModelObject mo) {
        //handle hierarchy etc
        Queue<ModelObject> q = new LinkedList<>();

        q.add(mo);
        while(!q.isEmpty()) {
            int levelSize = q.size();
            for(int i = 0; i < levelSize; i++) {
                ModelObject currNode = q.poll();

                if(mosByTypeAndById.get(currNode.getType()) == null) {
                    mosByTypeAndById.put(currNode.getType(), new HashMap<>());
                }

                mosByTypeAndById.get(currNode.getType()).put(currNode.getId(), currNode);

                for(ModelObject someChild : currNode.getChildren()) {
                    q.add(someChild);
                }
            }
        }
    }

    /**
     * There could be one peer only
     */
    private void addPeerRel(ModelObject mo, Set<ModelObject> peers) {
        /*for(ModelObject peer : peers) {
            //If type of a peer doesn't exist, create one
            if(mosByTypeAndById.get(peer.getType()) == null) {
                mosByTypeAndById.put(peer.getType(), new HashMap<>());
            }

            mosByTypeAndById.get(peer.getType()).put(peer.getId(), peer);
        }*/
    }

    @Override
    public void removeObjectById(String type, String id) {
        if(getObjectById(type, id) == null) {
            throw new IllegalStateException("Object " + id + " with type " + type + " doesn't exist'");
        }

        //start removing from the bottom up
        Deque<ModelObject> stack = new ArrayDeque<ModelObject>();
        Deque<ModelObject> stackToRemove = new ArrayDeque<>();

        ModelObject obj = getObjectById(type, id);
        stack.push(obj);
        while(!stack.isEmpty()) {
            int levelSize = stack.size();
            for(int i = 0; i < levelSize; i++) {
                ModelObject currNode = stack.pop();
                stackToRemove.push(currNode);

                if(mosByTypeAndById.get(currNode.getType()) == null) {
                    mosByTypeAndById.put(currNode.getType(), new HashMap<>());
                }

                mosByTypeAndById.get(currNode.getType()).put(currNode.getId(), currNode);

                for(ModelObject someChild : currNode.getChildren()) {
                    stack.push(someChild);
                }
            }
        }

        //Using simple iteration as to preserve state in case of failure, for example, if one of obj cannot be deleted
        int originalStackSize = stackToRemove.size();
        for(int i = 0; i <  originalStackSize; i++) {
            ModelObject objToRemove = stackToRemove.pop();

            //Detaching last one (top level element) form its parent
            if(stackToRemove.isEmpty()) {
                //TODO - this case is valid only for top level model objects such as device which have directly model as parent
                //((ModelObjectImpl)root).detachChild(objToRemove);
                ((ModelObjectImpl)root).detachDescendant(objToRemove);
            }
            mosByTypeAndById.get(objToRemove.getType()).remove(objToRemove.getId());
        }

        //TODO
        // Handle cases of non top level objects (cards)
    }

    @Override
    public void removePeerRelation(String type, String id, String typePeer1, String idPeer1, String typePeer2, String idPeer2) {

    }

    @Override
    public void removeRelativeRelation(String type, String id, String relativeType, String relativeId) {

    }

    private void index(ModelObject mo) {
        // Index
        final Map<String, ModelObject> mosById = mosByTypeAndById.computeIfAbsent(mo.getType(), e -> new HashMap<>());
        mosById.put(mo.getId(), mo);

        // Recurse
        for (ModelObject child : mo.getChildren()) {
            index(child);
        }
    }
}
