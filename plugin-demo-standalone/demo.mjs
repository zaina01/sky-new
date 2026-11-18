import { parseArgs } from 'node:util';
const { values } = parseArgs({ options: { limit: { type: 'string', default: '2' } } });
const raw = process.env.dxqy || process.env.chinaTelecomAccount || '';
if (!raw) {
  console.error('请在安装实例环境变量中配置 dxqy 或 chinaTelecomAccount');
  process.exit(1);
}
const accounts = raw.split(process.env.dxqy ? '@' : '#').filter(Boolean).slice(0, Number(values.limit));
console.log(`已读取 ${accounts.length} 个账号条目；不输出账号内容`);
