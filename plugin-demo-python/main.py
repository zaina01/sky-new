def run(ctx):
    config = ctx.config.get()
    accounts = ctx.accounts.list()
    print(f"开始执行，共 {len(accounts)} 个账号")
    for account in accounts:
        ctx.data.put("runs", account["accountId"], {"message": config.get("message", "Hello from Python")})
    return ctx.success("执行完成", {"processed": len(accounts), "input": ctx.input})
