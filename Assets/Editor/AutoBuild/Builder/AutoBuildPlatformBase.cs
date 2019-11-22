using System;
using System.Collections.Generic;
using System.IO;
using UnityEditor;
using UnityEngine;

namespace AutoBuild
{
    public abstract class AutoBuildPlatformBase
    {
        public AutoBuildArgs buildArgs;
        /// <summary>
        /// 切换平台 
        /// </summary>
        public abstract void SwitchPlatform();

        /// <summary>
        /// 初始化数据
        /// </summary>
        public virtual bool ResetData()
        {
            buildArgs = ParseCommandLine();
            InitScriptSymbols();
            return true;
        }

        /// <summary>
        /// 设置宏定义
        /// </summary>
        protected virtual void InitScriptSymbols()
        {
            if (buildArgs == null)
                return;
            SetScriptingDefineSymbolActive("GameDev", buildArgs.enableGameDevelopment);
        }

        /// <summary>
        /// 开始打包
        /// </summary>
        public abstract void StartBuild();



        private bool TryParseOneArg(string arg, string prefix, out string result)
        {
            result = null;
            if (arg.StartsWith(prefix))
            {
                result = arg.Substring(prefix.Length).Trim();
                return true;
            }
            return false;
        }

        /// <summary>
        /// 解析命令行参数
        /// </summary>
        /// <returns></returns>
        protected AutoBuildArgs ParseCommandLine()
        {
            AutoBuildArgs buildArgs = new AutoBuildArgs();
            string[] args = Environment.GetCommandLineArgs();
            foreach (var arg in args)
            {
                Debug.Log(arg);
                if (TryParseOneArg(arg, "outputPath|", out var outputPath))
                {
                    buildArgs.outputPath = outputPath;
                    continue;
                }
                if (TryParseOneArg(arg, "buildVersionName|", out var buildVersionName))
                {
                    buildArgs.buildVersionName = buildVersionName;
                    continue;
                }
                if (TryParseOneArg(arg, "buildMode|", out var buildMode))
                {
                    buildArgs.buildMode = (AutoBuildArgs.BuildMode)int.Parse(buildMode);
                    continue;
                }
                if (TryParseOneArg(arg, "enableUnityDevelopment|", out var enableUnityDevelopment))
                {
                    buildArgs.enableUnityDevelopment = bool.Parse(enableUnityDevelopment);
                    continue;
                }
                if (TryParseOneArg(arg, "enableExportProject|", out var enableExportProject))
                {
                    buildArgs.enableExportProject = bool.Parse(enableExportProject);
                    continue;
                }
                if (TryParseOneArg(arg, "enableGameDevelopment|", out var enableGameDevelopment))
                {
                    buildArgs.enableGameDevelopment = bool.Parse(enableGameDevelopment);
                    continue;
                }
            }
            return buildArgs;
        }

        /// <summary>
        /// 获取BuildPlayerOptions
        /// </summary>
        /// <param name="buildArgs"></param>
        /// <returns></returns>
        protected BuildPlayerOptions GetBuildPlayerOptions(AutoBuildArgs buildArgs)
        {
            BuildPlayerOptions options = new BuildPlayerOptions();
            options.locationPathName = buildArgs.outputFinalPath;
            options.scenes = GetScenes();
            options.target = EditorUserBuildSettings.activeBuildTarget;
            options.targetGroup = EditorUserBuildSettings.selectedBuildTargetGroup;
            options.options = BuildOptions.None;
            if (buildArgs.enableUnityDevelopment)
            {
                options.options |= BuildOptions.Development;
            }
            if (buildArgs.enableExportProject)
            {
                options.options |= BuildOptions.AcceptExternalModificationsToPlayer;
            }
            if (buildArgs.buildMode == AutoBuildArgs.BuildMode.CSharpOnly)
            {
                options.options |= BuildOptions.BuildScriptsOnly;
            }
            return options;
        }

        /// <summary>
        /// 获取打包的场景
        /// </summary>
        /// <returns></returns>
        internal string[] GetScenes()
        {
            List<string> s = new List<string>();

            for (int i = 0; i < EditorBuildSettings.scenes.Length; i++)
            {
                if (EditorBuildSettings.scenes[i].enabled)
                {
                    s.Add(EditorBuildSettings.scenes[i].path);
                }
            }

            return s.ToArray();
        }

        /// <summary>
        /// 设置代码宏,[[请注意打包设置的时候对编辑器模式代码是当次无效，下次生效的]]
        /// </summary>
        /// <param name="macro"></param>
        /// <param name="active"></param>
        public static void SetScriptingDefineSymbolActive(string macro, bool active)
        {
            string[] symbolsArray = PlayerSettings.GetScriptingDefineSymbolsForGroup(EditorUserBuildSettings.selectedBuildTargetGroup).Split(new char[] { ';' }, StringSplitOptions.RemoveEmptyEntries);
            List<string> symbolsList = new List<string>(symbolsArray);
            bool contain = symbolsList.Contains(macro);
            bool changed = false;
            if (!contain && active)
            {
                symbolsList.Add(macro);
                changed = true;
            }
            else if (contain && !active)
            {
                symbolsList.RemoveAll((symbol) => symbol == macro);
                changed = true;
            }
            if (changed)
            {
                PlayerSettings.SetScriptingDefineSymbolsForGroup(EditorUserBuildSettings.selectedBuildTargetGroup, string.Join(";", symbolsList));
            }
        }

    }

}
