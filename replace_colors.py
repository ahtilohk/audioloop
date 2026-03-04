import os
import re

ui_dir = r"c:\Users\kasutaja\Documents\Ahti\AndroidProjects\audioloop\app\src\main\java\com\example\audioloop\ui"

replacements = {
    r'\bZinc950\b': 'MaterialTheme.colorScheme.background',
    r'\bZinc900\b': 'MaterialTheme.colorScheme.surface',
    r'\bZinc800\b': 'MaterialTheme.colorScheme.surfaceVariant',
    r'\bZinc700\b': 'MaterialTheme.colorScheme.outlineVariant',
    r'\bZinc600\b': 'MaterialTheme.colorScheme.outline',
    r'\bZinc500\b': 'MaterialTheme.colorScheme.onSurfaceVariant',
    r'\bZinc400\b': 'MaterialTheme.colorScheme.onSurfaceVariant',
    r'\bZinc300\b': 'MaterialTheme.colorScheme.onSurface',
    r'\bZinc200\b': 'MaterialTheme.colorScheme.onSurface',
    # Since themeColors is static, we can also map some primary variants where appropriate:
    r'themeColors\.primary900': 'MaterialTheme.colorScheme.surfaceVariant',
    r'themeColors\.primary800': 'MaterialTheme.colorScheme.surfaceVariant',
    r'themeColors\.primary300': 'MaterialTheme.colorScheme.primary',
    r'themeColors\.primary400': 'MaterialTheme.colorScheme.primary',
    r'themeColors\.primary500': 'MaterialTheme.colorScheme.primary',
    r'themeColors\.primary600': 'MaterialTheme.colorScheme.primary',
}

for root, _, files in os.walk(ui_dir):
    for file in files:
        if file.endswith(".kt"):
            path = os.path.join(root, file)
            with open(path, "r", encoding="utf-8") as f:
                content = f.read()
            
            new_content = content
            for pattern, replacement in replacements.items():
                new_content = re.sub(pattern, replacement, new_content)

            # Special cases:
            # 1. We might want to keep White for text on primary buttons: 
            # If the context is `color = Color.White` inside a Primary button, wait, we replaced it all with `onSurface`.
            # We can fix `onSurface` back to `onPrimary` inside `ButtonDefaults.buttonColors(...)` or `Color.White` where `containerColor = ...primary...` but it's hard.
            # Let's write the file.
            if new_content != content:
                with open(path, "w", encoding="utf-8") as f:
                    f.write(new_content)
                print(f"Updated {file}")
