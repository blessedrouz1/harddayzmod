package com.harddayz;

import com.harddayz.entity.Missile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("harddayz")
@PrefixGameTestTemplate(false)
public class MissileRotationGameTests {
    @GameTest(template = "empty")
    public static void entityRotationMatchesFlight(GameTestHelper h) {
        for (int yaw : new int[]{0, 45, 90, 179, -179, -90}) {
            for (int pitch : new int[]{-80, -45, 0, 45, 80}) {
                Vec3 velocity = Vec3.directionFromRotation(pitch, yaw).scale(0.8);
                Missile m = Missile.launch(h.getLevel(), Missile.Kind.GRAD,
                        new Vec3(0, 150, 0), velocity, new Vec3(0, 70, 500), null);
                try {
                    h.assertTrue(m.getLookAngle().distanceTo(velocity.normalize()) < 0.0001,
                            "Entity rotation disagrees with velocity at yaw " + yaw + ", pitch " + pitch);
                    var rotation = new org.joml.Quaternionf().rotationY((float) Math.toRadians(-m.getYRot()))
                            .rotateX((float) Math.toRadians(m.getXRot())).rotateY((float) Math.PI);
                    var nose = rotation.transform(new org.joml.Vector3f(0, 0, -1));
                    h.assertTrue(new Vec3(nose.x, nose.y, nose.z).distanceTo(velocity.normalize()) < 0.0001,
                            "Rendered nose disagrees with velocity");
                } finally { m.discard(); }
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void ballisticLaunchKeepsRailDirection(GameTestHelper h) throws Exception {
        Vec3 axis = Vec3.directionFromRotation(-40, 73);
        Missile m = Missile.launch(h.getLevel(), Missile.Kind.BALLISTIC,
                new Vec3(0, 150, 0), axis.scale(0.8), new Vec3(0, 70, 500), null);
        var guide = Missile.class.getDeclaredMethod("guide", net.minecraft.server.level.ServerLevel.class, Vec3.class);
        guide.setAccessible(true);
        var face = Missile.class.getDeclaredMethod("face", Vec3.class);
        face.setAccessible(true);
        try {
            Vec3 velocity = m.getDeltaMovement();
            for (int tick = 1; tick <= 4; tick++) {
                m.tickCount = tick;
                velocity = (Vec3) guide.invoke(m, h.getLevel(), velocity);
                h.assertTrue(velocity.normalize().distanceTo(axis) < 0.0001, "Launch angle reset on tick " + tick);
            }
            for (int tick = 5; tick <= 60; tick++) {
                Vec3 previous = velocity.normalize();
                m.tickCount = tick;
                velocity = (Vec3) guide.invoke(m, h.getLevel(), velocity);
                double angle = Math.acos(net.minecraft.util.Mth.clamp(previous.dot(velocity.normalize()), -1, 1));
                h.assertTrue(angle <= 0.051, "Ballistic rocket snapped during climb");
            }
            h.assertTrue(velocity.normalize().y > 0.99, "Rocket failed to climb after leaving rail");
            m.setYRot(73);
            face.invoke(m, new Vec3(0, 1, 0));
            h.assertTrue(m.getYRot() == 73, "Vertical flight reset yaw");
        } finally { m.discard(); }
        h.succeed();
    }
}
