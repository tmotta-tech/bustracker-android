import csv
import json
import os
from collections import defaultdict

# Paths
GTFS_DIR = os.path.join(os.environ['TEMP'], 'gtfs_rio')
OUTPUT_FILE = os.path.join(os.environ['TEMP'], 'route_shapes.json')

print("Processing GTFS data...")

# Step 1: Load routes (route_id -> route_short_name)
routes = {}
with open(os.path.join(GTFS_DIR, 'routes.txt'), 'r', encoding='utf-8') as f:
    reader = csv.DictReader(f)
    for row in reader:
        routes[row['route_id']] = row['route_short_name']

print(f"Loaded {len(routes)} routes")

# Step 2: Load trips (route_id -> shape_id, pick first shape per route)
route_to_shape = {}
with open(os.path.join(GTFS_DIR, 'trips.txt'), 'r', encoding='utf-8') as f:
    reader = csv.DictReader(f)
    for row in reader:
        route_id = row['route_id']
        shape_id = row['shape_id']
        if route_id not in route_to_shape and shape_id:
            route_to_shape[route_id] = shape_id

print(f"Mapped {len(route_to_shape)} routes to shapes")

# Step 3: Build line_name -> shape_id
line_to_shape = {}
for route_id, line_name in routes.items():
    if route_id in route_to_shape:
        line_to_shape[line_name] = route_to_shape[route_id]

print(f"Mapped {len(line_to_shape)} line names to shapes")

# Step 4: Get unique shape_ids we need
needed_shapes = set(line_to_shape.values())
print(f"Need {len(needed_shapes)} unique shapes")

# Step 5: Load shapes (only needed ones)
shapes = defaultdict(list)
with open(os.path.join(GTFS_DIR, 'shapes.txt'), 'r', encoding='utf-8') as f:
    reader = csv.DictReader(f)
    for row in reader:
        shape_id = row['shape_id']
        if shape_id in needed_shapes:
            shapes[shape_id].append({
                'seq': int(row['shape_pt_sequence']),
                'lat': float(row['shape_pt_lat']),
                'lng': float(row['shape_pt_lon'])
            })

print(f"Loaded {len(shapes)} shapes with coordinates")

# Step 6: Sort points by sequence and simplify
for shape_id in shapes:
    shapes[shape_id].sort(key=lambda x: x['seq'])
    # Simplify: keep every 5th point to reduce size
    simplified = shapes[shape_id][::5]
    # Always include first and last
    if shapes[shape_id][-1] not in simplified:
        simplified.append(shapes[shape_id][-1])
    shapes[shape_id] = [[p['lat'], p['lng']] for p in simplified]

# Step 7: Build final output (line_name -> coordinates)
output = {}
for line_name, shape_id in line_to_shape.items():
    if shape_id in shapes:
        output[line_name] = shapes[shape_id]

print(f"Output has {len(output)} lines with shapes")

# Write output
with open(OUTPUT_FILE, 'w', encoding='utf-8') as f:
    json.dump(output, f, separators=(',', ':'))

size_mb = os.path.getsize(OUTPUT_FILE) / (1024*1024)
print(f"Saved to {OUTPUT_FILE} ({size_mb:.2f} MB)")
