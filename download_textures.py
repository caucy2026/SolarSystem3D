"""下载所有纹理到 assets/textures/ 实现离线可用
来源: SSS (行星) + Steve Albers (卫星真实照片)
"""
import os
import sys
import urllib.request

ASSETS_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'app', 'src', 'main', 'assets', 'textures')
os.makedirs(ASSETS_DIR, exist_ok=True)

SOURCES = [
    ('https://www.solarsystemscope.com/textures/download/', [
        '2k_sun.jpg', '2k_mercury.jpg', '2k_venus_atmosphere.jpg',
        '2k_earth_daymap.jpg', '2k_mars.jpg', '2k_jupiter.jpg',
        '2k_saturn.jpg', '2k_saturn_ring_alpha.png', '2k_uranus.jpg', '2k_neptune.jpg',
        '2k_moon.jpg', '2k_stars_milky_way.jpg',
    ]),
    ('https://stevealbers.net/albers/sos/', [
        'jupiter/io/io_rgb_cyl.jpg',
        'jupiter/europa/europa_rgb_cyl_juno.png',
        'jupiter/ganymede/ganymede_4k.jpg',
        'jupiter/callisto/callisto_rgb_cyl.jpg',
        'saturn/titan/titan_rgb_cyl.jpg',
        'neptune/triton/triton_rgb_cyl_www.jpg',
    ]),
]

total = sum(len(files) for _, files in SOURCES)
n = 0
for base_url, files in SOURCES:
    for name in files:
        n += 1
        filename = name.split('/')[-1]
        filepath = os.path.join(ASSETS_DIR, filename)
        if os.path.exists(filepath) and os.path.getsize(filepath) > 5000:
            print(f'[{n}/{total}] SKIP {filename}')
            continue
        url = base_url + name
        print(f'[{n}/{total}] DOWNLOAD {filename} ...', end=' ', flush=True)
        try:
            req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
            resp = urllib.request.urlopen(req, timeout=60)
            data = resp.read()
            with open(filepath, 'wb') as f:
                f.write(data)
            print(f'OK ({len(data)//1024}KB)')
        except Exception as e:
            print(f'FAIL: {e}')

print('\nDone!')
total_bytes = sum(os.path.getsize(os.path.join(ASSETS_DIR, f))
            for f in os.listdir(ASSETS_DIR) if os.path.isfile(os.path.join(ASSETS_DIR, f)))
print(f'Total: {total_bytes // (1024*1024)} MB')
