package de.unibi.citec.clf.bonsai.skills.dialog.nlu

import de.unibi.citec.clf.bonsai.actuators.SpeechActuator
import de.unibi.citec.clf.bonsai.core.exception.CommunicationException
import de.unibi.citec.clf.bonsai.core.`object`.MemorySlotWriter
import de.unibi.citec.clf.bonsai.core.`object`.Sensor
import de.unibi.citec.clf.bonsai.core.time.Time
import de.unibi.citec.clf.bonsai.engine.model.AbstractSkill
import de.unibi.citec.clf.bonsai.engine.model.ExitStatus
import de.unibi.citec.clf.bonsai.engine.model.ExitToken
import de.unibi.citec.clf.bonsai.engine.model.config.ISkillConfigurator
import de.unibi.citec.clf.bonsai.engine.model.config.SkillConfigurationException
import de.unibi.citec.clf.bonsai.util.helper.SimpleNLUHelper
import de.unibi.citec.clf.btl.data.speechrec.LanguageType
import de.unibi.citec.clf.btl.data.speechrec.NLU
import java.lang.Exception
import java.util.concurrent.TimeUnit

/**
 * Wait for the robot to understand something with certain intents.
 *
 * <pre>
 *
 * Options:
 *  #_ANY:                [Boolean] (default: false)
 *                          -> Listen to any NLU, disables #_INTENTS
 *  #_INTENTS:            [String] Required (when not #_ANY)
 *                          -> List of intents to listen for separated by ';'
 *  #_TIMEOUT:            [Long] Optional (default: -1)
 *                          -> Amount of time waited to understand something
 *
 * Slots:
 *  NLUSlot: [NLU] (Write)
 *      -> Save the understood NLU
 *
 * ExitTokens:
 *  success:                something was understood (if ANY is true)
 *  success.{understood}:   intent {understood} given in intents was understood
 *  error.timeout:          Timeout reached (only used when timeout is set to a positive value)
 *
 * </pre>
 *
 * @author lruegeme
 */
class WaitForNLU : AbstractSkill() {

    companion object {
        private const val KEY_SET_LANGUAGE = "#_SET_LANGUAGE"
        private const val KEY_DEFAULT = "#_INTENTS"
        private const val KEY_TIMEOUT = "#_TIMEOUT"
        private const val KEY_ANY = "#_ANY"
        private const val KEY_SENSOR = "#_SENSOR_KEY"
    }

    private var speechActuator: SpeechActuator? = null
    private var possible_intents: List<String> = mutableListOf()

    private var timeout: Long = -1
    private var any = false
    private var sensorkey = "NLUSensor"

    private var helper: SimpleNLUHelper? = null

    private var tokenSuccessPsTimeout: ExitToken? = null
    private val tokenMap = HashMap<String, ExitToken>()

    private var speechSensor: Sensor<NLU>? = null
    private var nluSlot: MemorySlotWriter<NLU>? = null
    private var langSlot: MemorySlotWriter<LanguageType>? = null

    override fun configure(configurator: ISkillConfigurator) {
        sensorkey = configurator.requestOptionalValue(KEY_SENSOR, sensorkey)
        any = configurator.requestOptionalBool(KEY_ANY, any)
        if (!any) {
            possible_intents = configurator.requestValue(KEY_DEFAULT).split(";")
            for (nt in possible_intents) {
                if (nt.isBlank()) continue
                tokenMap[nt] = configurator.requestExitToken(ExitStatus.SUCCESS().ps(nt))
            }
        } else if (configurator.hasConfigurationKey(KEY_DEFAULT)) {
            throw SkillConfigurationException("cant use $KEY_ANY and $KEY_DEFAULT together")
        } else {
            tokenMap["any"] = configurator.requestExitToken(ExitStatus.SUCCESS())
        }

        timeout = configurator.requestOptionalInt(KEY_TIMEOUT, timeout.toInt()).toLong()

        speechSensor = configurator.getSensor<NLU>(sensorkey, NLU::class.java)
        nluSlot = configurator.getWriteSlot<NLU>("NLUSlot", NLU::class.java)

        if (timeout > 0) {
            tokenSuccessPsTimeout = configurator.requestExitToken(ExitStatus.ERROR().ps("timeout"))
        }

        if (configurator.requestOptionalBool(KEY_SET_LANGUAGE, false)) {
            langSlot = configurator.getWriteSlot("Language", LanguageType::class.java)
        }

        speechActuator = configurator.getActuator("SpeechActuator", SpeechActuator::class.java)
    }

    override fun init(): Boolean {
        if (timeout > 0) {
            logger.debug("using timeout of $timeout ms")
            timeout += Time.currentTimeMillis()
        }

        try {
            logger.debug("Enabling ASR")
            speechActuator?.enableASR(true)?.get(500,TimeUnit.MILLISECONDS)
        } catch (ex: Exception) {
            logger.warn(ex)
        }

        helper = SimpleNLUHelper(speechSensor, true)
        helper!!.startListening()
        return true
    }

    override fun execute(): ExitToken {
        if (!helper!!.hasNewUnderstanding()) {
            if (timeout > 0) {
                if (Time.currentTimeMillis() > timeout) {
                    logger.info("timeout reached")
                    return tokenSuccessPsTimeout!!
                }
            }

            return ExitToken.loop(50)
        }
        logger.debug("have new understanding...")

        val understood = helper!!.allNLUs
        if (any) {
            try {
                nluSlot?.memorize<NLU>(understood[0])
                langSlot?.memorize(LanguageType(understood[0].lang))
                logger.info("memorized: " + understood[0])
                return tokenMap["any"]!!
            } catch (e: CommunicationException) {
                return ExitToken.fatal()
            }
        } else for (intent in possible_intents) {
            for (nt in understood) {
                if (intent == nt.intent) {
                    try {
                        nluSlot?.memorize<NLU>(nt)
                        langSlot?.memorize(LanguageType(nt.lang))
                    } catch (e: CommunicationException) {
                        logger.error("Can not write terminals $intent to memory.", e)
                        return ExitToken.fatal()
                    }
                    logger.info("understood \"$nt\"")
                    return tokenMap[intent]!!
                }
            }
        }
        return ExitToken.loop(50)
    }

    override fun end(curToken: ExitToken): ExitToken {
        speechSensor?.removeSensorListener(helper)
        return curToken
    }
}
