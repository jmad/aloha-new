/*
 * $Id: MadXModelDelegate.java,v 1.4 2009-03-16 16:38:11 kfuchsbe Exp $
 * 
 * $Date: 2009-03-16 16:38:11 $ $Revision: 1.4 $ $Author: kfuchsbe $
 * 
 * Copyright CERN, All Rights Reserved.
 */
package cern.accsoft.steering.aloha.model;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import cern.accsoft.steering.aloha.machine.manage.MachineElementsManager;
import cern.accsoft.steering.aloha.meas.data.align.AlignmentData;
import cern.accsoft.steering.aloha.model.adapt.JMadModelAdapter;
import cern.accsoft.steering.aloha.model.data.JMadModelOpticsData;
import cern.accsoft.steering.aloha.model.data.ModelOpticsData;
import cern.accsoft.steering.aloha.plugin.trim.meas.data.TrimData;
import cern.accsoft.steering.aloha.plugin.trim.meas.data.TrimValue;
import cern.accsoft.steering.aloha.util.JMadUtil;
import cern.accsoft.steering.jmad.domain.elem.Element;
import cern.accsoft.steering.jmad.domain.elem.JMadElementType;
import cern.accsoft.steering.jmad.domain.elem.impl.Bend;
import cern.accsoft.steering.jmad.domain.ex.JMadModelException;
import cern.accsoft.steering.jmad.domain.machine.Range;
import cern.accsoft.steering.jmad.domain.misalign.MisalignmentConfiguration;
import cern.accsoft.steering.jmad.model.JMadModel;
import cern.accsoft.steering.jmad.model.JMadModelListener;
import cern.accsoft.steering.util.acc.BeamNumber;
import cern.accsoft.steering.util.meas.read.filter.impl.NameListReadSelectionFilter;

/**
 * @author kfuchsbe
 */
public class JMadModelDelegate implements ModelDelegate {

    /** the logger for the class */
    private final static Logger logger = Logger.getLogger(JMadModelDelegate.class);

    /** The model we work with. */
    private final JMadModel model;

    /** The class which calculates the model-optics-data. */
    private final ModelOpticsData modelOpticsData;

    /** The model adapter (may be null!) */
    private final JMadModelAdapter modelAdapter;

    /**
     * If true the event to the listeners are note fired, false for normal notification behavior.
     */
    private boolean suppressEvents = false;

    /** the listeners to this delegate */
    private List<ModelDelegateListener> listeners = new ArrayList<ModelDelegateListener>();

    public JMadModelDelegate(JMadModel model, MachineElementsManager machineElementsManager,
            JMadModelAdapter modelAdapter) {
        this.modelOpticsData = new JMadModelOpticsData(this, machineElementsManager);
        this.model = model;
        this.modelAdapter = modelAdapter;
        if (this.model != null) {
            this.model.addListener(modelListener);
        }

        if (!machineElementsManager.isFilled()) {
            machineElementsManager.fill(model);
        }
    }

    /**
     * the listener, which we add to the madx-model
     */
    private JMadModelListener modelListener = new JMadModelListener() {
        @Override
        public void elementsChanged() {
        }

        @Override
        public void opticsChanged() {
        }

        @Override
        public void rangeChanged(Range newRange) {
        }

        @Override
        public void becameDirty() {
            /* just pass on to the listeners */
            fireBecameDirty();
        }

        @Override
        public void opticsDefinitionChanged() {
            /* nothing to do */
        }
    };

    @Override
    public void reset() {
        try {
            getJMadModel().reset();
        } catch (JMadModelException e) {
            logger.error("Error while resetting the model", e);
        }
    }

    /**
     * @return the madxModel
     */
    @Override
    public JMadModel getJMadModel() {
        return this.model;
    }

    /**
     * notify the listeners, that the model has changed
     */
    private void fireBecameDirty() {
        if (this.suppressEvents) {
            return;
        }
        for (ModelDelegateListener listener : this.listeners) {
            listener.becameDirty();
        }
    }

    @Override
    public void addListener(ModelDelegateListener listener) {
        this.listeners.add(listener);
    }

    @Override
    public void removeListener(ModelDelegateListener listener) {
        this.listeners.remove(listener);
    }

    @Override
    public void cleanup() {
        try {
            if (getJMadModel() != null) {
                getJMadModel().cleanup();
            }
        } catch (JMadModelException e) {
            logger.error("Error while claening up the model", e);
        }
    }

    @Override
    public NameListReadSelectionFilter createReadSelectionFilter(BeamNumber beamNumber) {
        Range activeRange = getJMadModel().getActiveRange();

        NameListReadSelectionFilter selection = new NameListReadSelectionFilter(
                activeRange.getElementNames(JMadElementType.CORRECTOR),
                activeRange.getElementNames(JMadElementType.MONITOR), beamNumber);
        return selection;
    }

    @Override
    public boolean isInitialized() {
        if (getJMadModel() == null) {
            return false;
        }
        return getJMadModel().isInitialized();
    }

    @Override
    public void applyTrim(TrimData trimData) {
        if (getJMadModel() == null) {
            return;
        }

        for (TrimValue value : trimData.getTrimValues()) {
            String name = value.getElementName();
            Element element = getJMadModel().getActiveRange().getElement(name);

            if (element == null) {
                logger.warn("Could not find element with name '" + name + "' in active range. Ignoring it.");
            }
            if (JMadElementType.CORRECTOR.isTypeOf(element)) {
                cern.accsoft.steering.jmad.domain.elem.impl.Corrector corrector = (cern.accsoft.steering.jmad.domain.elem.impl.Corrector) element;
                corrector.setKick(JMadUtil.convertPlane(value.getPlane()), value.getValue());
                logger.debug("Set kick (" + value.getPlane() + ") of " + value.getValue() + " [rad] to corrector '"
                        + corrector.getName() + "'");
            } else if (JMadElementType.BEND.isTypeOf(element)) {
                Bend bend = (Bend) element;
                bend.setAngle(value.getValue());
                logger.debug("Set angle of " + value.getValue() + " [rad] to bending magnet '" + bend.getName()
                        + "'. Plane (" + value.getPlane() + ") was ignored.");
            } else {
                logger.warn("Element '" + element.getName() + "' is neither a corrector nor a bending magnet. "
                        + "Cannot set trim. Ignoring it.");
            }
        }

        fireBecameDirty();
        //
        // TODO some kind of undo would be nice.
        //
    }

    @Override
    public void applyAlignment(AlignmentData alignmentData) {
        if (getJMadModel() == null) {
            return;
        }

        Range range = getJMadModel().getActiveRange();

        MadXAlignmentConverter converter = new MadXAlignmentConverter();
        List<MisalignmentConfiguration> misalignmentConfigurations = converter.createMadXMisalignments(alignmentData);

        for (MisalignmentConfiguration config : misalignmentConfigurations) {
            range.addMisalignment(config);
        }

        fireBecameDirty();
    }

    @Override
    public ModelOpticsData getModelOpticsData() {
        return this.modelOpticsData;
    }

    @Override
    public void setSuppressEvents(boolean suppressEvents) {
        this.suppressEvents = suppressEvents;
    }

    @Override
    public List<String> getMonitorRegexps() {
        if (this.modelAdapter == null) {
            return new ArrayList<String>();
        }
        return this.modelAdapter.getMonitorRegexps();
    }

}
