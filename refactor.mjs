import fs from 'fs';
import path from 'path';

const SRC_DIR = 'e:/salescrm/salescrm-be/src/main/java/com/salescms';
const LAYERS = ['controller', 'service', 'serviceImpl', 'repository', 'entity', 'response', 'request', 'dto', 'exception', 'config', 'util', 'helper', 'event', 'mapper'];

// Map to store new locations of classes: className -> { oldPackage, newPackage, filePath }
const classRegistry = new Map();

function determineLayer(content, fileName) {
  if (content.includes('@RestController') || fileName.endsWith('Controller.java')) return 'controller';
  if (content.includes('@Repository') || fileName.endsWith('Repository.java')) return 'repository';
  // Entities: usually have @Entity
  if (content.includes('@Entity') || content.includes('@Table')) return 'entity';
  if (content.includes('@Service')) return 'service';
  if (content.includes('@ControllerAdvice') || fileName.endsWith('Exception.java')) return 'exception';
  if (content.includes('@Configuration') || fileName.endsWith('Config.java')) return 'config';
  if (fileName.endsWith('Dto.java') || fileName.endsWith('Dtos.java')) return 'dto';
  if (fileName.endsWith('Request.java')) return 'request';
  if (fileName.endsWith('Response.java')) return 'response';
  if (fileName.endsWith('Util.java') || fileName.endsWith('Utils.java')) return 'util';
  if (fileName.endsWith('Helper.java')) return 'helper';
  if (fileName.endsWith('Event.java')) return 'event';
  if (fileName.endsWith('Mapper.java')) return 'mapper';
  
  // Default fallbacks based on name
  if (fileName.includes('Service')) return 'service';
  if (fileName.includes('Job') || fileName.includes('Seeder') || fileName.includes('Provisioning')) return 'util';
  
  if (content.includes(' record ')) return 'dto';
  
  return 'entity'; // fallback
}

function scanDir(dir) {
  const files = fs.readdirSync(dir);
  for (const file of files) {
    const fullPath = path.join(dir, file);
    if (fs.statSync(fullPath).isDirectory()) {
      if (!LAYERS.includes(file)) {
        scanDir(fullPath);
      }
    } else if (file.endsWith('.java')) {
      const content = fs.readFileSync(fullPath, 'utf8');
      const packageMatch = content.match(/^package\s+([a-zA-Z0-9_.]+);/m);
      if (packageMatch) {
        const oldPackage = packageMatch[1];
        const className = file.replace('.java', '');
        const layer = determineLayer(content, file);
        const newPackage = `com.salescms.${layer}`;
        const newPath = path.join(SRC_DIR, layer, file);
        classRegistry.set(className, { oldPackage, newPackage, oldPath: fullPath, newPath, layer });
      }
    }
  }
}

console.log("Scanning directory...");
scanDir(SRC_DIR);

LAYERS.forEach(layer => {
  const layerPath = path.join(SRC_DIR, layer);
  if (!fs.existsSync(layerPath)) {
    fs.mkdirSync(layerPath, { recursive: true });
  }
});

console.log("Moving files and updating imports...");

for (const [className, info] of classRegistry.entries()) {
  let content = fs.readFileSync(info.oldPath, 'utf8');
  
  content = content.replace(/^package\s+([a-zA-Z0-9_.]+);/m, `package ${info.newPackage};`);
  
  for (const [otherClassName, otherInfo] of classRegistry.entries()) {
    if (className === otherClassName) continue;
    
    const oldImportStmt = `import ${otherInfo.oldPackage}.${otherClassName};`;
    const newImportStmt = `import ${otherInfo.newPackage}.${otherClassName};`;
    
    if (content.includes(oldImportStmt)) {
      content = content.replace(oldImportStmt, newImportStmt);
    } else if (info.oldPackage === otherInfo.oldPackage && info.newPackage !== otherInfo.newPackage) {
      const usageRegex = new RegExp(`\\b${otherClassName}\\b`);
      if (usageRegex.test(content)) {
        content = content.replace(/^(package\s+[a-zA-Z0-9_.]+;)/m, `$1\nimport ${otherInfo.newPackage}.${otherClassName};`);
      }
    }
  }
  
  fs.unlinkSync(info.oldPath);
  fs.writeFileSync(info.newPath, content, 'utf8');
}

function cleanEmptyDirs(dir) {
  const files = fs.readdirSync(dir);
  for (const file of files) {
    const fullPath = path.join(dir, file);
    if (fs.statSync(fullPath).isDirectory()) {
      cleanEmptyDirs(fullPath);
    }
  }
  if (fs.readdirSync(dir).length === 0) {
    fs.rmdirSync(dir);
  }
}

console.log("Cleaning empty directories...");
try {
  cleanEmptyDirs(SRC_DIR);
} catch (e) {
  console.log("Some dirs not cleanable.", e.message);
}
console.log("Done refactoring.");
