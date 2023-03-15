package frc.robot.commands

import edu.wpi.first.math.controller.ProfiledPIDController
import edu.wpi.first.wpilibj.GenericHID.RumbleType
import edu.wpi.first.wpilibj.XboxController
import frc.robot.VisionUtils
import frc.robot.constants.VisionConstants
import frc.robot.subsystems.SwerveSubsystem
import edu.wpi.first.wpilibj2.command.*

fun SetPipeline(pipeline: VisionConstants.Pipelines) : Command {
    return InstantCommand({
        VisionUtils.setPipelineIndex("", pipeline.ordinal)
    })
}
class RumbleCheck(val controller: XboxController, val check: () -> Boolean): CommandBase() {
    override fun execute(){
        controller.setRumble(RumbleType.kBothRumble, (if (check()) 1.0 else 0.0));
    }

    override fun end(interrupted: Boolean) {
        controller.setRumble(RumbleType.kBothRumble, 0.0)
    }

    override fun isFinished(): Boolean {
        return !check()
  }
}

class XAlign(val driveSubsystem: SwerveSubsystem) : CommandBase() {
    val lineupXPID = ProfiledPIDController(VisionConstants.lineupXP, VisionConstants.lineupXI, VisionConstants.lineupXD, VisionConstants.lineupXConstraints)

    init {
        lineupXPID.setTolerance(1.0)
        lineupXPID.setGoal(0.0)
    }

    override fun execute() {
        val out = lineupXPID.calculate(VisionUtils.getTX("") - VisionConstants.alignmentTxOffset)

        driveSubsystem.drive(0.0, out, 0.0, false, false)
        println(lineupXPID.atSetpoint())
    }

    override fun isFinished(): Boolean {
        return !VisionUtils.getTV("") || lineupXPID.atSetpoint()
    }
}
class ZAlign(val driveSubsystem: SwerveSubsystem) : CommandBase() {
    val lineupZPID = ProfiledPIDController(VisionConstants.lineupZP, VisionConstants.lineupZI, VisionConstants.lineupZD, VisionConstants.lineupZConstraints)

    init {
        lineupZPID.setTolerance(1.0)
        lineupZPID.setGoal(VisionConstants.targetZ)
    }

    override fun execute() {
        val out = lineupZPID.calculate(VisionUtils.getLatestResults("").targetingResults.targets_Fiducials[0]?.robotPose_TargetSpace!![2])

        driveSubsystem.drive(out, 0.0, 0.0, false, false)
    }

    override fun isFinished(): Boolean {
        return !VisionUtils.getTV("") || lineupZPID.atSetpoint()
    }
}

class RotationAlign(val driveSubsystem: SwerveSubsystem) : CommandBase() {
    val lineupRotPID = ProfiledPIDController(VisionConstants.lineupRotP, VisionConstants.lineupRotI, VisionConstants.lineupRotD, VisionConstants.lineupRotConstraints)

    init {
        lineupRotPID.setTolerance(0.1)
        lineupRotPID.setGoal(0.0)
    }

    override fun execute() {
        val out = lineupRotPID.calculate(VisionUtils.getLatestResults("").targetingResults.targets_Fiducials[0]?.robotPose_TargetSpace!![5])

        driveSubsystem.drive(0.0, 0.0, -out, false, false)
    }

    override fun isFinished(): Boolean {
        return !VisionUtils.getTV("") || lineupRotPID.atSetpoint()
    }
}

fun AlignToAprilTag(driveSubsystem: SwerveSubsystem, controller: XboxController): Command{
  return(SequentialCommandGroup(
    SetPipeline(VisionConstants.Pipelines.APRILTAG),
    RumbleCheck(controller) { !VisionUtils.getTV("") },
      RotationAlign(driveSubsystem),
      XAlign(driveSubsystem)
  ))
}

fun AlignToCone(driveSubsystem: SwerveSubsystem, controller: XboxController): SequentialCommandGroup{
  return(SequentialCommandGroup(
    SetPipeline(VisionConstants.Pipelines.CONE),
    RumbleCheck(controller) { VisionUtils.getTV("") },
    XAlign(driveSubsystem)
  ))
}
fun AlignToRetroreflective(driveSubsystem: SwerveSubsystem, controller: XboxController): SequentialCommandGroup{
  return(SequentialCommandGroup(
    SetPipeline(VisionConstants.Pipelines.RETROREFLECTIVE),
    RumbleCheck(controller) { VisionUtils.getTV("") },
    XAlign(driveSubsystem)
  ))
}

fun AlignToCube(driveSubsystem: SwerveSubsystem, controller: XboxController): SequentialCommandGroup {
    return(SequentialCommandGroup(
        SetPipeline(VisionConstants.Pipelines.CUBE),
        RumbleCheck(controller) { VisionUtils.getTV("") },
        XAlign(driveSubsystem)
    ))
}