import os
import re

def fix_placeholders(match):
    content = match.group(2)
    # Find all %d, %s, %f etc. that are NOT preceded by $
    # (Simplified: just replace sequentially if there's more than one)
    placeholders = re.findall(r'%(?![0-9]+\$)[-.]*[0-9]*[dsf]', content)
    if len(placeholders) > 1:
        new_content = content
        for i, p in enumerate(placeholders):
            # Replace only the first occurrence of this placeholder type that hasn't been fixed
            # Actually, let's just replace them one by one
            pos_p = f"%{i+1}${p[1:]}"
            new_content = new_content.replace(p, pos_p, 1)
        return f'{match.group(1)}{new_content}{match.group(3)}'
    return match.group(0)

def fix_xml_file(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # 1. Fix single quotes (ensure they are escaped)
    # Replace ' with \' but only if not already escaped
    # Use negative lookbehind
    content = re.sub(r"(?<!\\)'", r"\'", content)
    
    # 2. Fix multiple placeholders (non-positional to positional)
    # Match <string ...>...</string>
    pattern = re.compile(r'(<string[^>]*>)(.*?)(</string>)', re.DOTALL)
    new_content = pattern.sub(fix_placeholders, content)
    
    # 3. Ensure XML declaration
    if not new_content.startswith('<?xml'):
        new_content = '<?xml version="1.0" encoding="utf-8"?>\n' + new_content
        
    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(new_content)

res_dir = r"c:\Users\kasutaja\Documents\Ahti\AndroidProjects\audioloop\app\src\main\res"
for root, dirs, files in os.walk(res_dir):
    for file in files:
        if file == "strings.xml":
            filepath = os.path.join(root, file)
            print(f"Fixing {filepath}")
            fix_xml_file(filepath)
