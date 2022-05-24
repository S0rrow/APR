import os
import sys
import datetime as dt

def main(args):
    timestamp = dt.datetime.now().strftime('%Y%m%d%H%M%S')
    root = os.getcwd()

    preparator = f"python3 -u /home/codemodel/leshen/APR/fix-target-env_new-old-file-generator.py '{root}' '{args[1]}' {timestamp}"
    launcher = f"python3 -u /home/codemodel/leshen/APR/fix-target-env_confix-launcher.py '{root}' '{args[1]}' {timestamp}"

    os.system(f"{preparator} & {launcher}")

if __name__ == '__main__':
    main(sys.argv)