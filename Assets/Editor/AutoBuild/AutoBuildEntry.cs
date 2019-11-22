using System.Collections;
using System.Collections.Generic;
using UnityEngine;
using UnityEditor;

namespace AutoBuild
{
    public static class AutoBuildEntry
    {

        [MenuItem("AutoBuild/BuildWindows")]
        static public void BuildWindows()
        {
            AutoBuildPlatformBase builder = new AutoBuildWindows();
            builder.SwitchPlatform();
            if (builder.ResetData())
            {
                builder.StartBuild();
            }
        }

        [MenuItem("AutoBuild/BuildAndroid")]
        static public void BuildAndroid()
        {
            AutoBuildPlatformBase builder = new AutoBuildAndroid();
            builder.SwitchPlatform();
            if (builder.ResetData())
            {
                builder.StartBuild();
            }
        }

        [MenuItem("AutoBuild/BuildiOS")]
        static public void BuildiOS()
        {
            AutoBuildPlatformBase builder = new AutoBuildiOS();
            builder.SwitchPlatform();
            if (builder.ResetData())
            {
                builder.StartBuild();
            }
        }
    }

}
