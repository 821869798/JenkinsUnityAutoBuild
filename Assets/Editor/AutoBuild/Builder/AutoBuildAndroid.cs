using System;
using System.Collections.Generic;
using System.IO;
using UnityEditor;

namespace AutoBuild
{
    public class AutoBuildAndroid : AutoBuildPlatformBase
    {
        public override void SwitchPlatform()
        {
            if (EditorUserBuildSettings.activeBuildTarget != BuildTarget.Android)
            {
                EditorUserBuildSettings.SwitchActiveBuildTarget(BuildTargetGroup.Android, BuildTarget.Android);
            }
            EditorUserBuildSettings.selectedBuildTargetGroup = BuildTargetGroup.Android;
        }

        public override bool ResetData()
        {
            if (!base.ResetData())
            {
                return false;
            }
            //设置输出路径
            string finalPathDir = Path.Combine(buildArgs.outputPath, buildArgs.buildVersionName);
            if (!Directory.Exists(finalPathDir))
            {
                Directory.CreateDirectory(finalPathDir);
            }
            buildArgs.outputFinalPath = Path.Combine(finalPathDir, PlayerSettings.productName + "_" + buildArgs.buildVersionName) + ".apk";
            return true;
        }

        public override void StartBuild()
        {
            BuildPipeline.BuildPlayer(GetBuildPlayerOptions(buildArgs));
        }


    }
}
