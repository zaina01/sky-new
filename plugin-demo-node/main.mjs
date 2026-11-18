export async function run(ctx) {
  const config = await ctx.config.get();
  const accounts = await ctx.accounts.list();
  console.log(`开始执行，共 ${accounts.length} 个账号`);
  for (const account of accounts) {
    await ctx.data.put('runs', account.accountId, { message: config.message ?? 'Hello from JavaScript' });
  }
  return ctx.success('执行完成', { processed: accounts.length, input: ctx.input });
}
