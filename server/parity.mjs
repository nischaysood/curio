import { topicHash, normaliseTopic } from './src/hash.js';
const cases = [
  ["How Compilers Work","STANDARD"],["how compilers work","STANDARD"],
  ["  HOW   COMPILERS  WORK  ","STANDARD"],["How compilers work!","STANDARD"],
  ["big o notation","STANDARD"],["big o notation","QUICK"],["big o notation","DEEP"],
  ["c programming","STANDARD"],["c++ programming","STANDARD"],["c# basics","STANDARD"],
  ["how interpreters work","STANDARD"],["émigré café","STANDARD"],
];
for (const [t,d] of cases) console.log(`${topicHash(t,d)}  ${JSON.stringify(normaliseTopic(t))}  ${d}`);
