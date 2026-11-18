import argparse
import os
import sys


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--limit', type=int, default=2)
    args = parser.parse_args()
    raw = os.getenv('dxqy') or os.getenv('chinaTelecomAccount', '')
    if not raw:
        print('请在安装实例环境变量中配置 dxqy 或 chinaTelecomAccount')
        return 1
    separator = '@' if os.getenv('dxqy') else '#'
    accounts = [item for item in raw.split(separator) if item][:args.limit]
    print(f'已读取 {len(accounts)} 个账号条目；不输出账号内容')
    return 0


if __name__ == '__main__':
    sys.exit(main())
