from __future__ import annotations

import os
from pathlib import Path
from typing import Iterable, Optional


RUN_KEY = r"Software\Microsoft\Windows\CurrentVersion\Run"
VALUE_NAME = "MobilePosSync"


class StartupRegistrationError(Exception):
    pass


def is_supported() -> bool:
    return os.name == "nt"


def _quote(value: str) -> str:
    escaped = value.replace('"', r'\"')
    return '"' + escaped + '"'


def startup_command(executable: Path, script: Optional[Path] = None, args: Iterable[str] = ()) -> str:
    parts = [_quote(str(Path(executable)))]
    if script is not None:
        parts.append(_quote(str(Path(script))))
    for arg in args:
        if any(char.isspace() for char in arg) or '"' in arg:
            parts.append(_quote(arg))
        else:
            parts.append(arg)
    return " ".join(parts)


def runtime_startup_command(executable: Path, app_script: Path, is_frozen: bool) -> str:
    if is_frozen:
        return startup_command(executable)
    return startup_command(executable, app_script, ("--gui",))


def is_enabled() -> bool:
    if not is_supported():
        return False
    try:
        import winreg

        with winreg.OpenKey(winreg.HKEY_CURRENT_USER, RUN_KEY, 0, winreg.KEY_READ) as key:
            winreg.QueryValueEx(key, VALUE_NAME)
            return True
    except FileNotFoundError:
        return False
    except OSError as exc:
        raise StartupRegistrationError(str(exc)) from exc


def set_enabled(
    enabled: bool,
    executable: Path,
    app_script: Optional[Path] = None,
    is_frozen: bool = False,
) -> None:
    if not is_supported():
        return
    try:
        import winreg

        with winreg.CreateKeyEx(winreg.HKEY_CURRENT_USER, RUN_KEY, 0, winreg.KEY_SET_VALUE) as key:
            if enabled:
                if app_script is None:
                    command = startup_command(executable)
                else:
                    command = runtime_startup_command(executable, app_script, is_frozen)
                winreg.SetValueEx(key, VALUE_NAME, 0, winreg.REG_SZ, command)
            else:
                try:
                    winreg.DeleteValue(key, VALUE_NAME)
                except FileNotFoundError:
                    pass
    except OSError as exc:
        raise StartupRegistrationError(str(exc)) from exc
