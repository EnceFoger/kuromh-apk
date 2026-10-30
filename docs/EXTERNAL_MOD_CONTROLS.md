# KUROMH external mod feature controls

KUROMH cannot guess features or runtime hooks of arbitrary third-party modules. To expose individual settings in KUROMH, a mod module must provide a manifest and a compatible configuration file.

## Files required in the module root

Place both files inside /data/adb/modules/<module-id>/ :

1. kuromh-controls.json — declares feature controls for the APK.
2. A plain config file ending in .cfg, .conf, .ini or .txt with flat key=value lines. The file must exist in the same directory.

Example kuromh-controls.json:

    {
      "title": "Example Mod",
      "config": "features.cfg",
      "controls": [
        {"key":"game_speed","label":"Game Speed","type":"slider","min":0.5,"max":5.0,"step":0.1},
        {"key":"damage_rate","label":"Damage Rate","type":"slider","min":1.0,"max":10.0,"step":0.5},
        {"key":"god_mode","label":"God Mode","type":"switch","on":"1","off":"0"}
      ]
    }

Example features.cfg:

    game_speed=1.0
    damage_rate=1.0
    god_mode=0

In KUROMH, open Settings -> Other Modules -> Control Mod Features.

## What the APK can and cannot do

The APK can load declared controls and update their matching configuration keys via root. The module itself must implement these options and read the config file.

A change only takes effect immediately if the mod actively reloads its config. Otherwise restart the game or reboot the device as required by the mod.

Third-party modules with proprietary config formats, paths outside their module root, or no existing configuration require a custom integration. KUROMH's existing Unity Speed and Damage controls are still exclusive to Kuro Mirror High.

## Contract details

- Root permission required.
- Only module IDs accepted by KUROMH module manager.
- File name must be a basename ending .cfg/.conf/.ini/.txt.
- Supported control types: slider and switch; max 24 controls.
- Each control needs a distinct alphanumeric/underscore key.
- Slider: min, max, step. Switch: on and off values (defaults: "1" and "0").
- No arbitrary shell commands, absolute config paths, directory traversal or symlink config files.
- Modules are responsible for honoring changed values and reloading their own state.

Install only modules you trust with root access.
