#!/bin/bash

# ==============================================================================
# Project: EBook Inspector Deployment Script
# Description: Automated pipeline to initialize Git, stage assets, commit,
#              and force-push the source repository safely to GitHub.
# Author: Murat
# License: GPL v3
# ==============================================================================

# Exit immediately if any command responds with a non-zero exit status code
set -e

echo "===================================================="
echo "🚀 Starting EBook Inspector deployment pipeline..."
echo "===================================================="

# Step 1: Purge any existing historical repository tracking data maps to avoid collision blocks
if [ -d ".git" ]; then
    echo "⚠️  Removing historical git directories layer..."
    rm -rf .git
fi

# Step 2: Initialize a clean, fresh atomic git tracking layout framework structure
echo "📦 Initializing clean source tracking infrastructure..."
git init

# Step 3: Stage all project structure directory resources and source files
echo "📂 Staging all local assets and core configuration assets..."
git add .

# Step 4: Record current baseline release commit parameters snapshot inside workspace metadata
echo "💾 Committing source snapshots to the master branch metadata pass..."
git commit -m "Android 36 EBook Inspector"

# Step 5: Establish external mapping boundaries connection straight to the remote GitHub repository
echo "🔗 Mapping origin pointer upstream to destination distribution channels..."
git remote add origin https://github.com/muratsivas76/EBook-Inspector.git

# Step 6: Recalibrate active root sequence indicators to uniform modern naming standards
echo "🌿 Configuring structural target layout parameters map to main..."
git branch -M main

# Step 7: Enforce absolute forced synchronization straight to target master repository
echo "📤 Force-pushing repository matrix directly onto online distribution systems..."
git push -u origin main --force

# Step 8: Purge volatile local git metadata block files to secure raw source environment directories
echo "🧹 Executing terminal cache memory blocks deallocation sweep..."
rm -rfv .git

echo "===================================================="
echo "✅ Deployment processing successfully executed!"
echo "===================================================="
