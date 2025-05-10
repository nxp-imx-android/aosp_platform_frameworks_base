/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.ambientcue.ui.shader

import com.android.systemui.surfaceeffects.shaderutil.ShaderUtilLibrary
import org.intellij.lang.annotations.Language

object BackgroundGlowShader {
    @Language("AGSL")
    val FRAG_SHADER =
        ShaderUtilLibrary.SHADER_LIB +
            """
    uniform float2 resolution;
    uniform half2 origin;
    uniform half radius;
    uniform half alpha;
    uniform half turbulencePhase;
    uniform half turbulenceAmount;
    uniform half turbulenceSize;
    layout(color) uniform half4 color1;
    layout(color) uniform half4 color2;
    layout(color) uniform half4 color3;

    half4 main(in float2 fragCoord) {
        float2 uv = fragCoord / resolution;
        half2 aspectRatio = half2(1.0, resolution.y / resolution.x);

        // Turbulence that distorts the circle shape
        vec3 noiseP = vec3(uv * aspectRatio, turbulencePhase) * turbulenceSize;
        float turbulence = (simplex3d(noiseP) * 0.5 + 0.5);
        float2 displacedCoord = fragCoord + float2(0.0, turbulence * turbulenceAmount);

        // Linear gradient, clipped to a radial shape
        half4 gradientLeft = mix(color1, color2, uv.x * 4. - 1);
        half4 gradientRight = mix(color2, color3, uv.x * 4 - 2.);
        half4 linearGradient = mix(gradientLeft, gradientRight, uv.x);
        half radialGradient = 1.0 - saturate(length(origin - displacedCoord) / radius);
        half4 combinedGradients = linearGradient * radialGradient;

        return combinedGradients;
    }
"""
                .trimIndent()
}
