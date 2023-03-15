// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.
package frc.robot

import edu.wpi.first.networktables.NetworkTableInstance
import edu.wpi.first.wpilibj.XboxController
import edu.wpi.first.wpilibj2.command.button.JoystickButton


import edu.wpi.first.wpilibj.XboxController.Axis
import edu.wpi.first.wpilibj2.command.*
import edu.wpi.first.wpilibj2.command.button.CommandXboxController
import edu.wpi.first.wpilibj2.command.button.POVButton
import edu.wpi.first.wpilibj2.command.button.Trigger

import frc.robot.commands.*
import frc.robot.constants.DrivetrainConstants
import frc.robot.subsystems.*
import java.util.ConcurrentModificationException

/**
 * This class is where the bulk of the robot should be declared. Since Command-based is a
 * "declarative" paradigm, very little robot logic should actually be handled in the [Robot]
 * periodic methods (other than the scheduler calls). Instead, the structure of the robot (including
 * subsystems, commands, and button mappings) should be declared here.
 */
class RobotContainer {
    val primaryController = XboxController(0)
    val secondaryController = XboxController(1)
    val pickAndPlace = PickAndPlaceSubsystem()
    val swerveSubsystem = SwerveSubsystem()
    val trajectories = Trajectories(pickAndPlace, swerveSubsystem)
    val testTrajectories = AutoTrajectories(pickAndPlace, swerveSubsystem)
    var cube: Boolean = false
    var middlePlace: Boolean = false
    var floor: Boolean = false
    var chute: Boolean = false
    //var pickup: Boolean = false



    /** The container for the robot. Contains subsystems, OI devices, and commands.  */
    init {
        // change this variable if youd like to use competition bindings (final tuning, driver practice), or test bindings
        // for (individual tuning and what not)
        configureButtonBindings()
    }

    // Do not modify.
    enum class Piece(val x: Int) {
        CONE(0),
        CUBE(1);

        companion object {
            fun fromInt(value: Int) = Piece.values().first { it.x == value }
        }
    }

    enum class Place(val x: Int) {
        HIGH(0),
        MID(1),
        LOW(2);

        companion object {
            fun fromInt(value: Int) = Place.values().first { it.x == value }
        }
    }

    /**
     * The Columbia controlscheme.
     *
     * **DO NOT MODIFY!** This is an official controlscheme and should not be modified unless debugging. To test your own
     * systems or controls, write another function.
     *
     * The primary controller has a high level control of the robot (Left joystick). They can drive the robot, and run
     * either a pick (X) or a place (Y) command. They can vary the speed of the robot's drivetrain (A).
     *
     * The secondary controller has the control to decide how a pick or place wants to be done. They can control whether
     * cones or cubes be picked up (A), where the cone or cube is placed (B), and whether to use vision (X).
     *
     * Should be used with a networktables utility to inspect the modes chosen.
     */
    private fun Columbia() {

        val nt = NetworkTableInstance.getDefault().getTable("DriverControl")

        var piece: Piece = Piece.CUBE
        var place: Place = Place.LOW
        var vision = false

        val pieceNT = nt.getIntegerTopic("Piece").publish()
        val placeNT = nt.getIntegerTopic("Place").publish()
        val visionNT = nt.getBooleanTopic("Vision").publish()

        val lockDrive: () -> Command = {
            StandardDrive(swerveSubsystem,
                { primaryController.leftY * DrivetrainConstants.maxSpeedMetersPerSecond / (if (primaryController.aButton) 1.0 else 2.0) },
                { 0.0 },
                { 0.0 },
                false,
                true)
        }

        pickAndPlace.defaultCommand = Base(pickAndPlace)

        val pickCommand: () -> Command = { ->
            val pnpCommand = if (piece == Piece.CONE) {
                LowPickCone(pickAndPlace)
            } else {
                LowPickCube(pickAndPlace)
            }

            val visionCommand = if (piece == Piece.CONE) {
                AlignToCone(swerveSubsystem, primaryController)
            } else {
                AlignToCube(swerveSubsystem, secondaryController)
            }

            ParallelCommandGroup(
                SequentialCommandGroup(
                    if (vision) visionCommand else InstantCommand({}),
                    lockDrive()
                ),
                pnpCommand
            )
        }

        val placeCommand: () -> Command = { ->
            val pnpCommand = if (piece == Piece.CONE) {
                when (place) {
                    Place.HIGH -> HighPlaceCone(pickAndPlace)
                    Place.MID -> MidPlaceCone(pickAndPlace)
                    Place.LOW -> FloorPlace(pickAndPlace)
                }
            } else {
                when (place) {
                    Place.HIGH -> HighPlaceCube(pickAndPlace)
                    Place.MID -> MidPlaceCube(pickAndPlace)
                    Place.LOW -> FloorPlace(pickAndPlace)
                }
            }

            val visionCommand = if (piece == Piece.CONE) {
                AlignToRetroreflective(swerveSubsystem, primaryController)
            } else {
                AlignToAprilTag(swerveSubsystem, secondaryController)
            }

            ParallelCommandGroup(
                SequentialCommandGroup(
                    if (vision) visionCommand else InstantCommand({}),
                    lockDrive()
                ),
                pnpCommand
            )
        }
        // Primary: Left joystick drives, A button enables faster speed, B button picks, Y button places, left and right triggers control intake speed.
        swerveSubsystem.defaultCommand = StandardDrive(swerveSubsystem,
            { primaryController.leftY * DrivetrainConstants.maxSpeedMetersPerSecond / (if (primaryController.aButton) 1.0 else 2.0) },
            { primaryController.leftX * DrivetrainConstants.maxSpeedMetersPerSecond / (if (primaryController.aButton) 1.0 else 2.0) },
            { primaryController.rightX * DrivetrainConstants.maxAngularSpeed / (if (primaryController.aButton) 1.0 else 2.0)},
            true,
            true)

        JoystickButton(primaryController, XboxController.Button.kB.value).whileTrue(pickCommand())
        JoystickButton(primaryController, XboxController.Button.kY.value).whileTrue(placeCommand())

        // Secondary: A button changes piece mode, B button changes place mode, X button changes vision mode.
        JoystickButton(secondaryController, XboxController.Button.kA.value).whileTrue(InstantCommand({
            piece = Piece.fromInt((piece.ordinal + 1) % (Piece.values().size))
            pieceNT.set(piece.ordinal.toLong())
        }))

        JoystickButton(secondaryController, XboxController.Button.kB.value).whileTrue(InstantCommand({
            place = Place.fromInt((place.ordinal + 1) % (Place.values().size))
            placeNT.set(place.ordinal.toLong())
        }))

        JoystickButton(secondaryController, XboxController.Button.kX.value).whileTrue(InstantCommand({
            vision = !vision
            visionNT.set(vision)
        }))

    }

    // Modify.
    private fun Challenger() {


        swerveSubsystem.defaultCommand = StandardDrive(swerveSubsystem,
            { primaryController.leftY * DrivetrainConstants.drivingSpeedScalar / 2.0 },
            { primaryController.leftX * DrivetrainConstants.drivingSpeedScalar / 2.0},
            { primaryController.rightX * DrivetrainConstants.rotationSpeedScalar  / 2.0},
            true,
            true)

        JoystickButton(primaryController, XboxController.Button.kB.value).whileTrue(
            StandardDrive(swerveSubsystem,
                { primaryController.leftY * DrivetrainConstants.drivingSpeedScalar },
                { primaryController.leftX * DrivetrainConstants.drivingSpeedScalar },
                { primaryController.rightX * DrivetrainConstants.rotationSpeedScalar },
                true,
                true
            ))
//TODO: The alignment to apriltag needs to happen before the pick and place command in sequential command order
//TODO: The alignment to apriltag needs to change to pegs for certain cases, or that needs to be added in in a different way cause right now, it would only place for cube shelves


        //PRIMARY CONtROLLER:

        //left trigger = pickup
        //right trigger = place

        //SECONDARY CONtROLLER
        //a = mid place
        //y = high place
        //x = cube
        //b = cone
        //bottom dpad = floor place
        //top dpad = chute pickup
        pickAndPlace.defaultCommand = Base(pickAndPlace)

        JoystickButton(primaryController, XboxController.Button.kX.value).whileTrue(
            RunCommand({
                swerveSubsystem.setX()
            }, swerveSubsystem)
        )
        JoystickButton(primaryController, XboxController.Button.kY.value).whileTrue(
            RunCommand({
                swerveSubsystem.zeroGyroAndOdometry()
            }, swerveSubsystem)
        )
        //Place:
        Trigger {primaryController.leftTriggerAxis > 0.2} .whileTrue(
            ControllerCommands(true, cube, middlePlace, floor, chute, pickAndPlace)
        )
        //Pickup:
        Trigger {primaryController.rightTriggerAxis > 0.2} .whileTrue(
            ControllerCommands(false, cube, middlePlace, floor, chute, pickAndPlace)
        )


        //SECONDARY CONtROLLER
        //a = mid place
        //y = high place
        //x = cube
        //b = cone
        //bottom dpad = floor place
        //top dpad = chute pickup

        //set middle place
        JoystickButton(secondaryController, XboxController.Button.kA.value).whileTrue(
            RunCommand({
                middlePlace = true
            })
        )
        //sets high place
        JoystickButton(secondaryController, XboxController.Button.kY.value).whileTrue(
            RunCommand({
                middlePlace = false
            })
        )
        //sets cube
        JoystickButton(secondaryController, XboxController.Button.kX.value).whileTrue(
            RunCommand({
                cube = true
            })
        )
        //sets cone
        JoystickButton(secondaryController, XboxController.Button.kB.value).whileTrue(
            RunCommand({
                cube = false
            })
        )
        POVButton(primaryController, 180).whileTrue(
            RunCommand({
                floor = true
            })
        )
        JoystickButton(secondaryController, XboxController.Button.kLeftBumper.value).whileTrue(
            VoltageArm(pickAndPlace, { 0.0 }, { 0.0 }, { 0.0 }, { 4.0 })
        )
        JoystickButton(secondaryController, XboxController.Button.kRightBumper.value).whileTrue(
            VoltageArm(pickAndPlace, { 0.0 }, { 0.0 }, { 0.0 }, { -4.0 })
        )
        Trigger {primaryController.leftTriggerAxis > 0.2} .whileTrue(
            VoltageArm(pickAndPlace, { primaryController.leftTriggerAxis * -2.0 }, { 0.0 }, { 0.0 }, { 0.0 })
        )
        Trigger {primaryController.rightTriggerAxis > 0.2} .whileTrue(
            VoltageArm(pickAndPlace, { primaryController.rightTriggerAxis * 4.0 }, { 0.0 }, { 0.0 }, { 0.0 })
        )
        JoystickButton(secondaryController, Axis.kLeftY.value).whileTrue(
           VoltageArm(pickAndPlace, { 0.0 }, { primaryController.leftX * -4.0 }, { 0.0 }, { 0.0 })
        )
        JoystickButton(secondaryController, Axis.kRightY.value).whileTrue(
            VoltageArm(pickAndPlace, { 0.0 }, { 0.0 }, { primaryController.rightY * -4.0 }, { 0.0 })
        )
    }

    private fun configureButtonBindings() {
    }

    val autonomousCommand: Command = RunCommand({trajectories.AutoBuilder()})
}
