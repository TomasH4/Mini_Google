package minigoogle

import java.nio.file.{Path, Paths}
import scala.annotation.tailrec

/** Lectura de opciones de línea de comandos con la forma `--nombre valor`. */
object Cli:
  type Flags = Map[String, String]

  /** Falla si una opción no tiene valor o no está en `allowed`. */
  def parse(args: List[String], allowed: Set[String]): Either[String, Flags] =
    @tailrec
    def loop(rest: List[String], acc: Flags): Either[String, Flags] = rest match
      case Nil => Right(acc)
      case flag :: value :: tail if flag.startsWith("--") && !value.startsWith("--") =>
        val name = flag.drop(2)
        if allowed.contains(name) then loop(tail, acc.updated(name, value))
        else Left(s"unknown option $flag")
      case flag :: _ => Left(s"option $flag needs a value")
    loop(args, Map.empty)

  def int(flags: Flags, name: String, default: Int, min: Int = 0): Either[String, Int] =
    flags.get(name) match
      case None => Right(default)
      case Some(raw) => raw.toIntOption.filter(_ >= min).toRight(s"--$name must be an integer >= $min (got '$raw')")

  def long(flags: Flags, name: String, default: Long): Either[String, Long] =
    flags.get(name) match
      case None => Right(default)
      case Some(raw) => raw.toLongOption.toRight(s"--$name must be an integer (got '$raw')")

  def path(flags: Flags, name: String): Option[Path] = flags.get(name).map(Paths.get(_))
