using System;
using System.Collections.Generic;
using System.IO;
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
            buildArgs.enableExportProject = true;       //导出到XCode工程
            //设置输出路径
            string finalPathDir = Path.Combine(buildArgs.outputPath, buildArgs.buildVersionName);
            finalPathDir = Path.Combine(finalPathDir, "xcode_project");
            if (!Directory.Exists(finalPathDir))
            {
                Directory.CreateDirectory(finalPathDir);
            }
            buildArgs.outputFinalPath = finalPathDir;
            return true;
        }

        public override void StartBuild()
        {
            BuildPipeline.BuildPlayer(GetBuildPlayerOptions(buildArgs));
        }

    }
}
