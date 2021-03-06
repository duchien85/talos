/*******************************************************************************
 * Copyright 2019 See AUTHORS file.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.talosvfx.talos.editor;

import com.talosvfx.talos.runtime.ParticleEmitterDescriptor;

public class ParticleEmitterWrapper {

    private String emitterName = "";
    public boolean isMuted;
    private boolean isSolo;
    private int position;

    private ParticleEmitterDescriptor moduleGraph;

    public ParticleEmitterDescriptor getGraph() {
        return moduleGraph;
    }

    public void setModuleGraph(ParticleEmitterDescriptor graph) {
        this.moduleGraph = graph;
    }

    public String getName() {
        return emitterName;
    }

    public void setName(String emitterName) {
        this.emitterName = emitterName;
    }

    public ParticleEmitterDescriptor getEmitter() {
        return moduleGraph;
    }
}
