using System;
using System.Collections.Generic;
using UnityEditor;

namespace AutoBuild
{
    public class AutoBuildiOS : AutoBuildPlatformBase
    {
        public override void SwitchPlatform()
        {
            if (EditorUserBuildSettings.activeBuildTarget != BuildTarget.iOS)
            {
                EditorUserBuildSettings.SwitchActiveBuildTarget(BuildTargetGroup.iOS, BuildTarget.iOS);
            }
            EditorUserBuildSettings.selectedBuildTargetGroup = BuildTargetGroup.iOS;
        }

        public override bool ResetData()
        {
            if (!base.ResetData())
            {
                return false;
            }
            return true;
        }

        public override void StartBuild()
        {

        }

    }
}
