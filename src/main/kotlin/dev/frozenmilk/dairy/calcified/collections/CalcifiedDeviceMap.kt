package dev.frozenmilk.dairy.calcified.collections

import com.qualcomm.robotcore.hardware.LynxModuleImuType
import com.qualcomm.robotcore.hardware.configuration.LynxConstants
import dev.frozenmilk.dairy.calcified.hardware.CalcifiedModule
import dev.frozenmilk.dairy.calcified.hardware.motor.AngleEncoder
import dev.frozenmilk.util.units.orientation.AngleBasedRobotOrientation
import dev.frozenmilk.dairy.calcified.hardware.servo.CalcifiedContinuousServo
import dev.frozenmilk.dairy.calcified.hardware.motor.CalcifiedEncoder
import dev.frozenmilk.dairy.calcified.hardware.sensor.CalcifiedIMU
import dev.frozenmilk.dairy.calcified.hardware.motor.CalcifiedMotor
import dev.frozenmilk.dairy.calcified.hardware.motor.DistanceEncoder
import dev.frozenmilk.dairy.calcified.hardware.servo.CalcifiedServo
import dev.frozenmilk.dairy.calcified.hardware.sensor.DigitalInput
import dev.frozenmilk.dairy.calcified.hardware.sensor.DigitalOutput
import dev.frozenmilk.dairy.calcified.hardware.servo.PWMDevice
import dev.frozenmilk.dairy.calcified.hardware.motor.TicksEncoder
import dev.frozenmilk.dairy.calcified.hardware.motor.UnitEncoder
import dev.frozenmilk.dairy.calcified.hardware.sensor.AnalogInput
import dev.frozenmilk.util.units.DistanceUnit
import dev.frozenmilk.util.units.AngleUnit

abstract class CalcifiedDeviceMap<T> internal constructor(protected val module: CalcifiedModule, private val map: MutableMap<Byte, T> = mutableMapOf()) : MutableMap<Byte, T> by map

class Motors internal constructor(module: CalcifiedModule) : CalcifiedDeviceMap<CalcifiedMotor>(module) {
	fun getMotor(port: Byte): CalcifiedMotor {
		// checks to confirm that the motor port is validly in range
		if (port !in LynxConstants.INITIAL_MOTOR_PORT until LynxConstants.INITIAL_MOTOR_PORT + LynxConstants.NUMBER_OF_MOTORS) throw IllegalArgumentException("$port is not in the acceptable port range [${LynxConstants.INITIAL_MOTOR_PORT}, ${LynxConstants.INITIAL_MOTOR_PORT + LynxConstants.NUMBER_OF_MOTORS - 1}]")
		this.putIfAbsent(port, CalcifiedMotor(module, port))
		return this[port]!!
	}
}

class PWMDevices internal constructor(module: CalcifiedModule) : CalcifiedDeviceMap<PWMDevice>(module) {
	fun getServo(port: Byte): CalcifiedServo {
		if (port !in LynxConstants.INITIAL_SERVO_PORT until LynxConstants.INITIAL_SERVO_PORT + LynxConstants.NUMBER_OF_SERVO_CHANNELS - 1) throw IllegalArgumentException("$port is not in the acceptable port range [${LynxConstants.INITIAL_SERVO_PORT}, ${LynxConstants.INITIAL_SERVO_PORT + LynxConstants.NUMBER_OF_SERVO_CHANNELS - 1}]")
		if (this.containsKey(port) && this[port] !is CalcifiedServo) {
			this[port] = CalcifiedServo(module, port)
		}
		return (this[port] as CalcifiedServo)
	}

	fun getContinuousServo(port: Byte): CalcifiedContinuousServo {
		if (port !in LynxConstants.INITIAL_SERVO_PORT until LynxConstants.INITIAL_SERVO_PORT + LynxConstants.NUMBER_OF_SERVO_CHANNELS - 1) throw IllegalArgumentException("$port is not in the acceptable port range [${LynxConstants.INITIAL_SERVO_PORT}, ${LynxConstants.INITIAL_SERVO_PORT + LynxConstants.NUMBER_OF_SERVO_CHANNELS - 1}]")
		if (this.containsKey(port) || this[port] !is CalcifiedContinuousServo) {
			this[port] = CalcifiedContinuousServo(module, port)
		}
		return (this[port] as CalcifiedContinuousServo)
	}
}

class Encoders internal constructor(module: CalcifiedModule) : CalcifiedDeviceMap<CalcifiedEncoder<*>>(module) {

	/**
	 * if the port is empty, makes a new [TicksEncoder], else, overrides the encoder on the port
	 */
	fun getTicksEncoder(port: Byte): TicksEncoder {
		// this is pretty much the same as the motors, as the encoders match the motors
		// checks to confirm that the encoder port is validly in range
		if (port !in LynxConstants.INITIAL_MOTOR_PORT until LynxConstants.INITIAL_MOTOR_PORT + LynxConstants.NUMBER_OF_MOTORS) throw IllegalArgumentException("$port is not in the acceptable port range [${LynxConstants.INITIAL_MOTOR_PORT}, ${LynxConstants.INITIAL_MOTOR_PORT + LynxConstants.NUMBER_OF_MOTORS - 1}]")
		if (!contains(port) || this[port] !is TicksEncoder) {
			this[port] = TicksEncoder(module, port)
			this[port]?.reset()
		}
		return (this[port] as TicksEncoder)
	}

	/**
	 * This method is useful for if you have your own [UnitEncoder] overrides, for your own types, most of the time you want to use one of the other get<type>Encoder methods on this module
	 *
	 * @return Overrides the encoder on the port with a [UnitEncoder] of the supplied type, with the [ticksPerUnit] specified
	 */
	inline fun <reified T : UnitEncoder<*>> getEncoder(lazySupplier: (TicksEncoder) -> T, port: Byte): T {
		val ticksEncoder = getTicksEncoder(port)
		this[port] = lazySupplier(ticksEncoder)
		return this[port] as? T ?: throw IllegalStateException("something went wrong while creating a new encoder, this shouldn't be reachable")
	}

	inline fun <reified T : UnitEncoder<*>> getEncoder(port: Byte): T? {
		return this[port] as? T
	}
	/**
	 * overrides the encoder on the port with an [AngleEncoder], with the [ticksPerRevolution] specified, that outputs values as [angleUnit]
	 */
	fun getAngleEncoder(port: Byte, ticksPerRevolution: Double, angleUnit: AngleUnit): AngleEncoder {
		return getEncoder({
			AngleEncoder(it, ticksPerRevolution, angleUnit)
		}, port)
	}

	fun getDistanceEncoder(port: Byte, ticksPerUnit: Double, distance: DistanceUnit): DistanceEncoder {
		return getEncoder({
			DistanceEncoder(it, ticksPerUnit, distance)
		}, port)
	}
}

class I2CDevices internal constructor(module: CalcifiedModule) : CalcifiedDeviceMap<Any>(module){
	fun getIMU(port: Byte, imuType: LynxModuleImuType, angleBasedRobotOrientation: AngleBasedRobotOrientation): CalcifiedIMU {
		if (port !in 0 until LynxConstants.NUMBER_OF_I2C_BUSSES) throw IllegalArgumentException("$port is not in the acceptable port range [0, ${LynxConstants.NUMBER_OF_I2C_BUSSES - 1}]")
		if (!this.containsKey(port) || this[port] !is CalcifiedIMU || (this[port] as CalcifiedIMU).imuType != imuType) {
			this[port] = CalcifiedIMU(imuType, module, port, angleBasedRobotOrientation)
		}
		return (this[port] as CalcifiedIMU)
	}

	@JvmOverloads
	fun getIMU_BHI260(port: Byte, angleBasedRobotOrientation: AngleBasedRobotOrientation = AngleBasedRobotOrientation()) = this.getIMU(port, LynxModuleImuType.BHI260, angleBasedRobotOrientation)

	@JvmOverloads
	fun getIMU_BNO055(port: Byte, angleBasedRobotOrientation: AngleBasedRobotOrientation = AngleBasedRobotOrientation()) = this.getIMU(port, LynxModuleImuType.BNO055, angleBasedRobotOrientation)
}

class DigitalChannels internal constructor(module: CalcifiedModule) : CalcifiedDeviceMap<Any>(module) {
	fun getInput(port: Byte): DigitalInput {
		if (port !in 0 until LynxConstants.NUMBER_OF_DIGITAL_IOS) throw IllegalArgumentException("$port is not in the acceptable port range [0, ${LynxConstants.NUMBER_OF_DIGITAL_IOS - 1}]")
		if (this.containsKey(port) || this[port] !is DigitalInput) {
			this[port] = DigitalInput(module, port)
		}
		return (this[port] as DigitalInput)
	}
	fun getOutput(port: Byte): DigitalOutput {
		if (port !in 0 until LynxConstants.NUMBER_OF_DIGITAL_IOS) throw IllegalArgumentException("$port is not in the acceptable port range [0, ${LynxConstants.NUMBER_OF_DIGITAL_IOS - 1}]")
		if (this.containsKey(port) || this[port] !is DigitalOutput) {
			this[port] = DigitalInput(module, port)
		}
		return (this[port] as DigitalOutput)
	}
}

class AnalogInputs internal constructor(module: CalcifiedModule) : CalcifiedDeviceMap<AnalogInput>(module) {
	fun getInput(port: Byte): AnalogInput {
		if (port !in 0 until LynxConstants.NUMBER_OF_ANALOG_INPUTS) throw IllegalArgumentException("$port is not in the acceptable port range [0, ${LynxConstants.NUMBER_OF_ANALOG_INPUTS - 1}]")
		this.putIfAbsent(port, AnalogInput(module, port))
		return this[port]!!
	}
}