using System.Collections;
using System.Collections.Generic;
using UnityEngine;
using UnityEditor;
using System.IO;

namespace AutoBuild
{
    public class AutoBuildWindows : AutoBuildPlatformBase
    {

        public override void SwitchPlatform()
        {
            if (EditorUserBuildSettings.activeBuildTarget != BuildTarget.StandaloneWindows64)
            {
                EditorUserBuildSettings.SwitchActiveBuildTarget(BuildTargetGroup.Standalone, BuildTarget.StandaloneWindows64);
            }
            EditorUserBuildSettings.selectedBuildTargetGroup = BuildTargetGroup.Standalone;
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
            buildArgs.outputFinalPath = Path.Combine(finalPathDir, PlayerSettings.productName) + ".exe";
            //初始化平台宏
            return true;
        }

        public override void StartBuild()
        {
            BuildPipeline.BuildPlayer(GetBuildPlayerOptions(buildArgs));
        }

    }
}