package de.unibi.citec.clf.bonsai.skills.deprecated.personPerception.unsupported;


import de.unibi.citec.clf.bonsai.actuators.SpeechActuator;
import de.unibi.citec.clf.bonsai.core.object.MemorySlot;
import de.unibi.citec.clf.bonsai.core.object.MemorySlotWriter;
import de.unibi.citec.clf.bonsai.core.time.Time;
import de.unibi.citec.clf.bonsai.engine.model.AbstractSkill;
import de.unibi.citec.clf.bonsai.engine.model.ExitStatus;
import de.unibi.citec.clf.bonsai.engine.model.ExitToken;
import de.unibi.citec.clf.bonsai.engine.model.config.ISkillConfigurator;
import java.io.IOException;
import java.util.Date;
import java.util.concurrent.ExecutionException;

import de.unibi.citec.clf.btl.data.speechrec.Language;
import org.apache.log4j.Logger;

/**
 * This state will save a new face to a FaceIdentification Slot.
 *
 * It starts starting timeout when person was facing robot once.
 *
 * <pre>
 * Special exit statuses:
 * <code>error</code> if no person is standing in front of the robot.
 * </pre>
 *
 *
 * @author lziegler
 */
public class AddNewFaceToList extends AbstractSkill {
    /*
     * What the robot will say.
     */

    private static final String STATE_INTRO1 = "Please look into my upper camera ";
    private static final String STATE_INTRO3 = "I am learning your face now.";
    private static final String STATE_FINISHED = "Thank you!";

    // used tokens
    private ExitToken tokenSuccess;

    /*
     * Actuators used by this state.
     */
    private SpeechActuator speechActuator;

    /*
     * Sensors used by this state.
     */
    // unsupported private Sensor<FaceIdentificationList> faceSensor;

    // unsupported private MemorySlot<FaceIdentificationList> knownPersonMemorySlot;
    
    private MemorySlotWriter<String> newIndex;

    /**
     * Additional time to learn person.
     */
    private static final long TIME_TO_LEARN = 8000;
    /**
     * Time to sleep after each iteration.
     */
    private static final int TIME_TO_SLEEP = 500;

    /**
     * Time when start learning.
     */
    private Date start = Time.now();

    private static Logger logger = Logger.getLogger(StoreNewFace.class);
    // unsupported private FaceIdentificationList faceList = new FaceIdentificationList();

    @Override
    public void configure(ISkillConfigurator configurator) {
        speechActuator = configurator.getActuator(
                "SpeechActuator", SpeechActuator.class);
        /*faceSensor = configurator.getSensor(
                "FaceIdentificationSensor", FaceIdentificationList.class);

        knownPersonMemorySlot = configurator.getSlot(
                "faceList", FaceIdentificationList.class);*/

        newIndex = configurator.getWriteSlot(
                "newIndex", String.class);
        
        // request all tokens that you plan to return from other methods
        tokenSuccess = configurator.requestExitToken(ExitStatus.SUCCESS());

        
        
    }

    @Override
    public boolean init() {

        say(STATE_INTRO1, false);
        start = Time.now();
        say(STATE_INTRO3, true);
        
        /*
        try {
            faceList = knownPersonMemorySlot.recall();
        } catch (Exception ex) {
            logger.debug("created new facelist");
            faceList = new FaceIdentificationList();
        }
        
        //quick and dirty
        if(faceList==null){
            faceList = new FaceIdentificationList();
        }
        */
        return true;
    }

    @Override
    public ExitToken execute() {

        // check if learning time has not elapsed
        if ((new Date().getTime() - start.getTime()) < TIME_TO_LEARN) {
            return ExitToken.loop(TIME_TO_SLEEP);
        }

        // read faces
        /*
        FaceIdentificationList list;
        try {
            list = faceSensor.readLast(100);
            if (list == null || list.size() == 0) {
                throw new IOException();
            }
        } catch (IOException | InterruptedException e) {
            logger.error("got no data from face sensor");
            return ExitToken.loop();
        }

        // find largest face
        FaceIdentification largestFace = null;
        double maxFaceSize = 0;
        for (FaceIdentification face : list) {
            double size = face.getRegionWidth() * face.getRegionHeight();
            if (largestFace == null || size > maxFaceSize) {
                largestFace = face;
                maxFaceSize = size;
            }
        }

        // check if face has valid ID
        if (largestFace.getClassId() < 0) {
            logger.error("largest face has illegal ID: "+largestFace.getClassId()+". Retry !!!");
            return ExitToken.loop(TIME_TO_SLEEP);
        }

        // save face
        try {
            faceList.addIdentification(largestFace);
            knownPersonMemorySlot.memorize(faceList);
            newIndex.memorize(String.valueOf(largestFace.getClassId()));
        } catch (CommunicationException e) {
            
            logger.error("Cannot communicate with memory: " + e.getMessage());
            logger.debug("Cannot communicate with memory: " + e.getMessage(), e);
            return ExitToken.fatal();
        }*/

        return tokenSuccess;
    }

    @Override
    public ExitToken end(ExitToken curStatus) {
        say(STATE_FINISHED, true);
        return curStatus;
    }

    /**
     * Use speech actuator to say something and catch IO exception.
     *
     * @param text Text to be said.
     * @param async if <code>true</code>, the method call will return as fast as possible, blocks otherwise
     */
    private void say(String text, boolean async) {
        try {
            if (async) {
                speechActuator.sayAsync(text, Language.EN);
            } else {
                speechActuator.sayAsync(text, Language.EN).get();
            }
        } catch (IOException ex) {
            // Not so bad. The robot just doesn't say anything.
            logger.warn(ex.getMessage());
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
