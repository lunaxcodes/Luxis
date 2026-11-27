"""Build Luxis's directional blur presets using vanilla post-process passes."""

import json
from pathlib import Path


root = Path(__file__).resolve().parents[1] / "luxis-ui-resources/assets/luxis/post_effect"
root.mkdir(parents=True, exist_ok=True)
for axis, direction in enumerate(((1.0, 0.0), (0.0, 1.0))):
    for strength, radius in enumerate((2.0, 4.0, 6.0), 1):
        effect = {
            "targets": {"swap": {}},
            "passes": [
                {
                    "vertex_shader": "minecraft:core/screenquad",
                    "fragment_shader": "minecraft:post/box_blur",
                    "inputs": [{"sampler_name": "In", "target": "minecraft:main", "bilinear": True}],
                    "output": "swap",
                    "uniforms": {"BlurConfig": [
                        {"name": "BlurDir", "type": "vec2", "value": direction},
                        {"name": "Radius", "type": "float", "value": radius},
                    ]},
                },
                {
                    "vertex_shader": "minecraft:core/screenquad",
                    "fragment_shader": "minecraft:post/blit",
                    "inputs": [{"sampler_name": "In", "target": "swap"}],
                    "output": "minecraft:main",
                    "uniforms": {"BlitConfig": [
                        {"name": "ColorModulate", "type": "vec4", "value": [1.0, 1.0, 1.0, 1.0]},
                    ]},
                },
            ],
        }
        (root / f"turn_{axis}_{strength}.json").write_text(json.dumps(effect, indent=2) + "\n", encoding="utf-8")
